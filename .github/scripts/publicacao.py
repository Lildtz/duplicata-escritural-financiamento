"""Guardas da publicação GHCR; somente biblioteca padrão e CLI Docker/Git."""

import base64
import hashlib
import json
import os
import re
import subprocess
import sys
import tarfile
from collections import Counter
from datetime import datetime
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener

REPOSITORIO = "Lildtz/duplicata-escritural-financiamento"
FONTE = f"https://github.com/{REPOSITORIO}"
APLICACOES = ("duplicata-escritural-financiamento-api", "duplicata-escritural-signataria-api")
MANIFESTOS = ("application/vnd.oci.image.manifest.v1+json", "application/vnd.docker.distribution.manifest.v2+json")


def executar(*argumentos, capturar=False):
    return subprocess.run(argumentos, check=True, text=True, capture_output=capturar).stdout


def saida(nome, valor):
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as arquivo:
        arquivo.write(f"{nome}={valor}\n")


def validar_evento(evento, repositorio):
    run = evento.get("workflow_run") or {}
    if not (
        repositorio == REPOSITORIO
        and (evento.get("repository") or {}).get("full_name") == REPOSITORIO
        and evento.get("action") == "completed"
        and run.get("status") == "completed"
        and run.get("conclusion") == "success"
        and run.get("event") == "push"
        and run.get("head_branch") == "main"
        and (run.get("head_repository") or {}).get("full_name") == REPOSITORIO
        and run.get("name") == "CI"
        and run.get("path") == ".github/workflows/ci.yml"
        and isinstance(run.get("head_sha"), str)
        and re.fullmatch(r"[0-9a-f]{40}", run.get("head_sha", ""))
    ):
        raise ValueError("Origem do CI não autorizada")
    return run["head_sha"]


def autorizar():
    if os.environ.get("GITHUB_EVENT_NAME") != "workflow_run":
        raise ValueError("Gatilho não autorizado")
    evento = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    sha = validar_evento(evento, os.environ["GITHUB_REPOSITORY"])
    executar("git", "merge-base", "--is-ancestor", sha, "refs/remotes/origin/main")
    criado = executar("git", "show", "-s", "--format=%cI", sha, capturar=True).strip()
    saida("sha", sha)
    saida("created", criado)


def identidade():
    sha = os.environ["SOURCE_SHA"]
    app = os.environ["APP"]
    criado = os.environ["CREATED"]
    if app not in APLICACOES or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Aplicação ou SHA inválido")
    if datetime.fromisoformat(criado).tzinfo is None:
        raise ValueError("Data OCI exige timezone")
    return f"ghcr.io/lildtz/{app}", f"sha-{sha}"


class SemRedirecionamento(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Não encaminhar Authorization a outro destino.


def obter(url, headers):
    with build_opener(SemRedirecionamento()).open(Request(url, headers=headers), timeout=30) as resposta:
        return resposta.read(), resposta.headers


def manifesto():
    imagem, tag = identidade()
    nome = imagem.removeprefix("ghcr.io/")
    credencial = base64.b64encode(f"{os.environ['GITHUB_ACTOR']}:{os.environ['GHCR_TOKEN']}".encode()).decode()
    consulta = urlencode({"service": "ghcr.io", "scope": f"repository:{nome}:pull"})
    corpo, _ = obter(f"https://ghcr.io/token?{consulta}", {"Authorization": f"Basic {credencial}"})
    token = json.loads(corpo)["token"]
    try:
        corpo, headers = obter(f"https://ghcr.io/v2/{nome}/manifests/{tag}", {
            "Authorization": f"Bearer {token}", "Accept": ", ".join(MANIFESTOS),
        })
    except HTTPError as erro:
        if erro.code == 404:
            codigos = {item["code"] for item in json.loads(erro.read()).get("errors", [])}
            if codigos and codigos <= {"MANIFEST_UNKNOWN", "NAME_UNKNOWN"}:
                return None
        raise ValueError(f"Consulta GHCR falhou: HTTP {erro.code}") from None
    digest = "sha256:" + hashlib.sha256(corpo).hexdigest()
    dados = json.loads(corpo)
    if headers.get("Docker-Content-Digest") != digest or dados.get("mediaType") not in MANIFESTOS:
        raise ValueError("Manifesto GHCR incompatível ou digest inválido")
    return {"digest": digest, "config": dados["config"]["digest"]}


def validar_imagem(referencia):
    imagem = json.loads(executar("docker", "image", "inspect", referencia, capturar=True))[0]
    labels = imagem["Config"].get("Labels") or {}
    if not (
        imagem["Os"] == "linux" and imagem["Architecture"] == "amd64"
        and imagem["Config"]["User"] == "10001:10001"
        and labels.get("org.opencontainers.image.source") == FONTE
        and labels.get("org.opencontainers.image.revision") == os.environ["SOURCE_SHA"]
        and labels.get("org.opencontainers.image.created") == os.environ["CREATED"]
    ):
        raise ValueError("Imagem incompatível com origem, plataforma, labels ou usuário esperados")


def consultar():
    imagem, tag = identidade()
    existente = manifesto()
    if existente:
        referencia = f"{imagem}@{existente['digest']}"
        executar("docker", "pull", referencia)
        validar_imagem(referencia)
        executar("docker", "tag", referencia, f"{imagem}:{tag}")
    saida("digest", existente["digest"] if existente else "")


def avaliar_vulnerabilidades(relatorio):
    resultados = relatorio.get("Results", [])
    tem_so = any(item.get("Class") == "os-pkgs" for item in resultados)
    tem_java = any(
        item.get("Class") == "lang-pkgs" and item.get("Type") == "jar" and item.get("Packages")
        for item in resultados
    )
    if not tem_so or not tem_java:
        raise ValueError("Relatório deve incluir SO e dependências do runtime Java")
    vulnerabilidades = [v for item in resultados for v in item.get("Vulnerabilities", [])]
    contagem = Counter(v["Severity"] for v in vulnerabilidades)
    print("Vulnerabilidades por severidade:", json.dumps(dict(contagem), sort_keys=True))
    if contagem["CRITICAL"]:
        raise ValueError("Publicação bloqueada: vulnerabilidade CRITICAL requer avaliação")


def publicar():
    imagem, tag = identidade()
    if manifesto() is not None:
        raise ValueError("Tag apareceu após a consulta; não sobrescrever. Reexecutar para verificar/reutilizar")
    with tarfile.open(Path(os.environ["RUNNER_TEMP"]) / "imagem.tar") as arquivo:
        imagens = json.load(arquivo.extractfile("manifest.json"))
        if len(imagens) != 1:
            raise ValueError("Archive deve conter somente a imagem analisada")
        config = "sha256:" + hashlib.sha256(arquivo.extractfile(imagens[0]["Config"]).read()).hexdigest()
    executar("docker", "push", f"{imagem}:{tag}")
    publicado = manifesto()
    if publicado is None or publicado["config"] != config:
        raise ValueError("Config publicada diverge da imagem construída")
    saida("digest", publicado["digest"])


def registrar():
    imagem, tag = identidade()
    digest = os.environ["DIGEST"]
    remoto = manifesto()
    if remoto is None or remoto["digest"] != digest:
        raise ValueError("Tag remota mudou antes do registro de rastreabilidade")
    registro = {
        "imagem": imagem, "shaGit": os.environ["SOURCE_SHA"], "tag": tag,
        "plataforma": "linux/amd64", "digestOCI": digest,
        "workflow": f"{FONTE}/actions/runs/{int(os.environ['GITHUB_RUN_ID'])}",
    }
    destino = Path(os.environ["RUNNER_TEMP"]) / f"{os.environ['APP']}-publicacao.json"
    destino.write_text(json.dumps(registro, indent=2) + "\n", encoding="utf-8")
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as resumo:
        resumo.write("| Campo | Valor |\n|---|---|\n")
        for campo, valor in registro.items():
            resumo.write(f"| {campo} | `{valor}` |\n")


def main():
    comando = sys.argv[1]
    if comando == "autorizar":
        autorizar()
    elif comando == "consultar":
        consultar()
    elif comando == "validar":
        imagem, tag = identidade()
        validar_imagem(f"{imagem}:{tag}")
    elif comando == "vulnerabilidades":
        caminho = Path(os.environ["SCAN_DIR"]) / f"{os.environ['APP']}-vulnerabilidades.json"
        avaliar_vulnerabilidades(json.loads(caminho.read_text(encoding="utf-8")))
    elif comando == "publicar":
        publicar()
    elif comando == "registrar":
        registrar()
    else:
        raise ValueError("Comando desconhecido")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as erro:
        # Não imprimir respostas HTTP, credenciais ou detalhes de subprocessos.
        motivo = str(erro) if type(erro) is ValueError else type(erro).__name__
        print(f"Publicação interrompida: {motivo}", file=sys.stderr)
        sys.exit(1)
