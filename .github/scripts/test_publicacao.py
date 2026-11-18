import hashlib
import io
import json
import os
import subprocess
import sys
import tarfile
import tempfile
import unittest
from contextlib import chdir, redirect_stdout
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError

import publicacao as p


def evento(sha="a" * 40):
    return {
        "action": "completed", "repository": {"full_name": p.REPOSITORIO},
        "workflow_run": {
            "status": "completed", "conclusion": "success", "event": "push",
            "head_branch": "main", "head_repository": {"full_name": p.REPOSITORIO},
            "head_sha": sha, "name": "CI", "path": ".github/workflows/ci.yml",
        },
    }


class PublicacaoTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.ambiente = patch.dict(os.environ, {
            "SOURCE_SHA": "a" * 40, "APP": p.APLICACOES[0], "CREATED": "2026-11-18T10:44:57+00:00",
            "GITHUB_REPOSITORY": p.REPOSITORIO, "GITHUB_EVENT_NAME": "workflow_run",
            "GITHUB_OUTPUT": str(Path(self.temp.name) / "output"),
            "GITHUB_EVENT_PATH": str(Path(self.temp.name) / "evento.json"),
            "RUNNER_TEMP": self.temp.name, "SCAN_DIR": self.temp.name,
            "GITHUB_ACTOR": "operador-teste", "GHCR_TOKEN": "credencial-ficticia-de-teste",
            "GITHUB_RUN_ID": "123", "GITHUB_STEP_SUMMARY": str(Path(self.temp.name) / "summary"),
        })
        self.ambiente.start()
        self.addCleanup(self.ambiente.stop)

    def test_evento_positivo(self):
        self.assertEqual(p.validar_evento(evento(), p.REPOSITORIO), "a" * 40)

    def test_origens_negativas(self):
        casos = {
            "conclusion": ["failure", "cancelled", "skipped", None],
            "event": ["pull_request", "pull_request_target", "workflow_dispatch"],
            "head_branch": ["feature", "main; echo injetado"],
            "head_repository": [{"full_name": "invasor/fork"}, None],
            "head_sha": ["a" * 39, "b" * 41, "$(echo injetado)", "A" * 40, "-main", None],
            "status": ["in_progress"], "name": ["Outro CI"], "path": [".github/workflows/falso.yml"],
        }
        for campo, valores in casos.items():
            for valor in valores:
                with self.subTest(campo=campo, valor=valor):
                    dados = evento()
                    dados["workflow_run"][campo] = valor
                    with self.assertRaises(ValueError):
                        p.validar_evento(dados, p.REPOSITORIO)
        for repositorio in ("invasor/fork", "lildtz/duplicata-escritural-financiamento"):
            with self.assertRaises(ValueError):
                p.validar_evento(evento(), repositorio)
        dados = evento()
        dados["repository"]["full_name"] = "invasor/fork"
        with self.assertRaises(ValueError):
            p.validar_evento(dados, p.REPOSITORIO)
        dados = evento()
        dados["action"] = "requested"
        with self.assertRaises(ValueError):
            p.validar_evento(dados, p.REPOSITORIO)

    def test_evento_pr_nao_executa_git(self):
        dados = evento()
        dados["workflow_run"]["event"] = "pull_request"
        Path(os.environ["GITHUB_EVENT_PATH"]).write_text(json.dumps(dados), encoding="utf-8")
        with patch.object(p, "executar") as executar:
            with self.assertRaises(ValueError):
                p.autorizar()
            executar.assert_not_called()

    def test_outro_gatilho_nao_executa_git(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "push"}), patch.object(p, "executar") as executar:
            with self.assertRaises(ValueError):
                p.autorizar()
            executar.assert_not_called()

    def test_ancestralidade_real_inclui_sha_antigo_e_rejeita_fora_da_main(self):
        def git(*args):
            return subprocess.check_output(("git", *args), text=True, stderr=subprocess.DEVNULL).strip()

        with chdir(self.temp.name):
            git("init", "--initial-branch=main")
            git("-c", "user.name=Teste", "-c", "user.email=teste@example.invalid", "commit", "--allow-empty", "-m", "base")
            base = git("rev-parse", "HEAD")
            git("checkout", "-b", "feature")
            git("-c", "user.name=Teste", "-c", "user.email=teste@example.invalid", "commit", "--allow-empty", "-m", "fora")
            fora = git("rev-parse", "HEAD")
            git("checkout", "main")
            git("-c", "user.name=Teste", "-c", "user.email=teste@example.invalid", "commit", "--allow-empty", "-m", "main-avancou")
            git("update-ref", "refs/remotes/origin/main", git("rev-parse", "HEAD"))
            for sha, permitido in ((base, True), (fora, False), ("b" * 40, False)):
                Path(os.environ["GITHUB_EVENT_PATH"]).write_text(json.dumps(evento(sha)), encoding="utf-8")
                if permitido:
                    p.autorizar()
                    self.assertIn(f"sha={base}", Path(os.environ["GITHUB_OUTPUT"]).read_text(encoding="utf-8"))
                else:
                    with self.assertRaises(subprocess.CalledProcessError):
                        p.autorizar()

    def resposta_manifesto(self, **alteracoes):
        dados = {"mediaType": p.MANIFESTOS[0], "config": {"digest": "sha256:" + "c" * 64}}
        dados.update(alteracoes)
        corpo = json.dumps(dados).encode()
        return corpo, {"Docker-Content-Digest": "sha256:" + hashlib.sha256(corpo).hexdigest()}

    def test_manifesto_verifica_hash_e_destino(self):
        resposta = self.resposta_manifesto()
        with patch.object(p, "obter", side_effect=[(b'{"token":"ficticio"}', {}), resposta]) as obter:
            self.assertEqual(p.manifesto()["digest"], resposta[1]["Docker-Content-Digest"])
            for chamada in obter.call_args_list:
                self.assertTrue(chamada.args[0].startswith("https://ghcr.io/"))

    def test_somente_404_conhecido_significa_ausente(self):
        for codigo in ("MANIFEST_UNKNOWN", "NAME_UNKNOWN"):
            erro = HTTPError("https://ghcr.io/", 404, "ausente", {}, io.BytesIO(json.dumps({"errors": [{"code": codigo}]}).encode()))
            with patch.object(p, "obter", side_effect=[(b'{"token":"ficticio"}', {}), erro]):
                self.assertIsNone(p.manifesto())
        for status in (401, 403, 404, 429, 500):
            erro = HTTPError("https://ghcr.io/", status, "falha", {}, io.BytesIO(b'{"errors":[{"code":"DENIED"}]}'))
            with patch.object(p, "obter", side_effect=[(b'{"token":"ficticio"}', {}), erro]):
                with self.assertRaises(ValueError):
                    p.manifesto()

    def test_timeout_nao_significa_ausente(self):
        with patch.object(p, "obter", side_effect=TimeoutError):
            with self.assertRaises(TimeoutError):
                p.manifesto()

    def test_digest_corrompido_e_index_bloqueados(self):
        corpo, headers = self.resposta_manifesto()
        respostas = [(corpo + b" ", headers), self.resposta_manifesto(mediaType="application/vnd.oci.image.index.v1+json")]
        for resposta in respostas:
            with patch.object(p, "obter", side_effect=[(b'{"token":"ficticio"}', {}), resposta]):
                with self.assertRaises(ValueError):
                    p.manifesto()

    def test_redirecionamento_nao_encaminha_credencial(self):
        self.assertIsNone(p.SemRedirecionamento().redirect_request(None, None, 302, "", {}, "https://outro.invalid"))

    def imagem(self):
        return {"Os": "linux", "Architecture": "amd64", "Config": {"User": "10001:10001", "Labels": {
            "org.opencontainers.image.source": p.FONTE,
            "org.opencontainers.image.revision": os.environ["SOURCE_SHA"],
            "org.opencontainers.image.created": os.environ["CREATED"],
        }}}

    def test_imagem_plataforma_usuario_e_labels(self):
        with patch.object(p, "executar", return_value=json.dumps([self.imagem()])):
            p.validar_imagem("imagem-ficticia")
        for campo, valor in (("Os", "windows"), ("Architecture", "arm64"), ("User", "0"), ("revision", "b" * 40), ("source", "https://outro.invalid"), ("created", "outra")):
            dados = self.imagem()
            if campo in ("Os", "Architecture"):
                dados[campo] = valor
            elif campo == "User":
                dados["Config"][campo] = valor
            else:
                dados["Config"]["Labels"]["org.opencontainers.image." + campo] = valor
            with patch.object(p, "executar", return_value=json.dumps([dados])):
                with self.assertRaises(ValueError):
                    p.validar_imagem("imagem-ficticia")

    def test_tag_existente_reutilizada_sem_push(self):
        with patch.object(p, "manifesto", return_value={"digest": "sha256:" + "d" * 64}), patch.object(p, "executar") as executar, patch.object(p, "validar_imagem"):
            p.consultar()
            self.assertEqual([c.args[:2] for c in executar.call_args_list], [("docker", "pull"), ("docker", "tag")])
            self.assertIn("digest=sha256:", Path(os.environ["GITHUB_OUTPUT"]).read_text(encoding="utf-8"))

    def test_tag_ausente_nao_executa_docker(self):
        with patch.object(p, "manifesto", return_value=None), patch.object(p, "executar") as executar:
            p.consultar()
            executar.assert_not_called()

    def test_tag_incompativel_nao_e_reutilizada(self):
        with patch.object(p, "manifesto", return_value={"digest": "sha256:" + "d" * 64}), patch.object(p, "executar") as executar, patch.object(p, "validar_imagem", side_effect=ValueError):
            with self.assertRaises(ValueError):
                p.consultar()
            self.assertEqual(executar.call_count, 1)

    def test_recupera_push_aceito_com_resposta_perdida_sem_republicar(self):
        with tarfile.open(Path(self.temp.name, "imagem.tar"), "w") as arquivo:
            for nome, dados in (("manifest.json", b'[{"Config":"config.json"}]'), ("config.json", b"{}")):
                info = tarfile.TarInfo(nome)
                info.size = len(dados)
                arquivo.addfile(info, io.BytesIO(dados))
        with patch.object(p, "manifesto", side_effect=[None, TimeoutError]), patch.object(p, "executar") as executar:
            with self.assertRaises(TimeoutError):
                p.publicar()
            self.assertEqual(executar.call_args.args[:2], ("docker", "push"))
        with patch.object(p, "manifesto", return_value={"digest": "sha256:" + "d" * 64}), patch.object(p, "executar") as executar, patch.object(p, "validar_imagem"):
            p.consultar()
            self.assertNotIn(("docker", "push"), [c.args[:2] for c in executar.call_args_list])

    def test_identidade_rejeita_aplicacao_e_sha_injetados(self):
        imagem, tag = p.identidade()
        self.assertEqual(imagem, "ghcr.io/lildtz/" + p.APLICACOES[0])
        self.assertEqual(tag, "sha-" + "a" * 40)
        for ambiente in ({"APP": "../outra"}, {"APP": "$(echo injetado)"}, {"SOURCE_SHA": "a" * 40 + "\n"}):
            with patch.dict(os.environ, ambiente), self.assertRaises(ValueError):
                p.identidade()

    def test_corrida_nao_sobrescreve(self):
        with patch.object(p, "manifesto", return_value={"digest": "ja-existe"}), patch.object(p, "executar") as executar:
            with self.assertRaises(ValueError):
                p.publicar()
            executar.assert_not_called()

    def test_publicacao_confere_config_e_registra_digest(self):
        config_bytes = b'{"config":"ficticia"}'
        esperado = "sha256:" + hashlib.sha256(config_bytes).hexdigest()
        with tarfile.open(Path(self.temp.name, "imagem.tar"), "w") as arquivo:
            for nome, dados in (("manifest.json", b'[{"Config":"config.json"}]'), ("config.json", config_bytes)):
                info = tarfile.TarInfo(nome)
                info.size = len(dados)
                arquivo.addfile(info, io.BytesIO(dados))
        for config, valido in ((esperado, True), ("divergente", False)):
            with patch.object(p, "manifesto", side_effect=[None, {"config": config, "digest": "sha256:" + "d" * 64}]), patch.object(p, "executar") as executar:
                if valido:
                    p.publicar()
                else:
                    with self.assertRaises(ValueError):
                        p.publicar()
                self.assertEqual(executar.call_args.args[:2], ("docker", "push"))

    def test_critica_bloqueia_mesmo_sem_correcao_e_high_e_reportada(self):
        for severidade in ("HIGH", "CRITICAL"):
            relatorio = {"Results": [{"Class": "os-pkgs", "Vulnerabilities": [{"Severity": severidade}]}, {"Class": "lang-pkgs", "Type": "jar", "Packages": [{"Name": "biblioteca-java"}]}]}
            with redirect_stdout(io.StringIO()):
                if severidade == "HIGH":
                    p.avaliar_vulnerabilidades(relatorio)
                else:
                    with self.assertRaises(ValueError):
                        p.avaliar_vulnerabilidades(relatorio)
        with self.assertRaises(ValueError):
            p.avaliar_vulnerabilidades({"Results": [{"Class": "os-pkgs"}]})

    def test_relatorio_sem_java_ou_inventario_vazio_e_rejeitado(self):
        for resultado in (
            {"Class": "lang-pkgs", "Type": "npm", "Packages": [{"Name": "pacote-node"}]},
            {"Class": "lang-pkgs", "Type": "jar", "Packages": []},
            {"Class": "lang-pkgs", "Type": "jar"},
        ):
            with self.subTest(resultado=resultado), self.assertRaises(ValueError):
                p.avaliar_vulnerabilidades({"Results": [{"Class": "os-pkgs"}, resultado]})
        with self.assertRaises(ValueError):
            p.avaliar_vulnerabilidades({})

    def test_relatorio_com_inventario_java_sem_cves_e_aceito(self):
        with redirect_stdout(io.StringIO()):
            p.avaliar_vulnerabilidades({"Results": [
                {"Class": "os-pkgs"},
                {"Class": "lang-pkgs", "Type": "jar", "Packages": [{"Name": "biblioteca-java"}]},
            ]})

    def test_relatorio_ausente_ou_invalido_falha_sem_publicar(self):
        script = str(Path(p.__file__).resolve())
        ambiente = dict(os.environ, PYTHONIOENCODING="utf-8")
        resultado = subprocess.run((sys.executable, "-B", script, "vulnerabilidades"), env=ambiente, text=True, capture_output=True)
        self.assertEqual(resultado.returncode, 1)
        Path(self.temp.name, p.APLICACOES[0] + "-vulnerabilidades.json").write_text("{incompleto", encoding="utf-8")
        resultado = subprocess.run((sys.executable, "-B", script, "vulnerabilidades"), env=ambiente, text=True, capture_output=True)
        self.assertEqual(resultado.returncode, 1)

    def test_rastreabilidade_confere_digest_remoto(self):
        digest = "sha256:" + "d" * 64
        with patch.dict(os.environ, {"DIGEST": digest}), patch.object(p, "manifesto", return_value={"digest": digest}):
            p.registrar()
            registro = json.loads(Path(self.temp.name, p.APLICACOES[0] + "-publicacao.json").read_text(encoding="utf-8"))
            self.assertEqual(registro["digestOCI"], digest)
            self.assertEqual(registro["plataforma"], "linux/amd64")
            self.assertEqual(registro["workflow"], p.FONTE + "/actions/runs/123")
        with patch.dict(os.environ, {"DIGEST": digest}), patch.object(p, "manifesto", return_value={"digest": "alterado"}):
            with self.assertRaises(ValueError):
                p.registrar()


if __name__ == "__main__":
    unittest.main()
