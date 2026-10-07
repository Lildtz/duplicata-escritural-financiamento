---

name: code-change-verification
description: Verifique mudanças de código antes de considerar uma tarefa concluída. Use após implementar features, correções, refatorações, alterações de persistência, integrações, dependências ou mudanças relevantes de configuração. Valide build, testes, diff, escopo, arquivos indevidos, dependências, segurança e aderência ao AGENTS.md.
---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

# Code Change Verification

Use esta skill depois de alterações relevantes no código.

O objetivo é responder:

> A mudança realmente funciona, está limitada ao escopo solicitado e mantém o projeto saudável?

Leia o `AGENTS.md` e considere a estratégia de implementação definida anteriormente.

Não considere uma tarefa concluída apenas porque o código compila na IDE.

---

# 1. Identifique o que mudou

Comece verificando:

```bash
git status
git diff
```

Quando houver arquivos staged:

```bash
git diff --cached
```

Identifique:

* arquivos criados;
* arquivos alterados;
* arquivos removidos;
* dependências adicionadas;
* configurações alteradas;
* mudanças de schema;
* mudanças de comportamento.

Compare isso com o escopo originalmente planejado.

---

# 2. Verifique o escopo

Pergunte:

* todas as alterações são necessárias para a tarefa?
* existem mudanças não relacionadas?
* ocorreu drive-by refactoring?
* algum arquivo foi alterado incidentalmente?
* houve atualização de dependência sem necessidade?
* alguma configuração global foi modificada desnecessariamente?

Remova alterações não relacionadas antes de considerar a tarefa concluída.

Prefira diffs pequenos e compreensíveis.

---

# 3. Compile o código

Execute o build relevante.

Para o projeto completo no Windows:

```powershell
.\gradlew.bat clean build
```

Quando fizer sentido validar apenas um módulo primeiro:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:build
```

ou:

```powershell
.\gradlew.bat :duplicata-escritural-escrituradora-api:build
```

O build completo deve ser executado quando a mudança puder impactar mais de um módulo.

Não declare sucesso se o build falhar.

---

# 4. Execute os testes relevantes

Execute testes compatíveis com a alteração realizada.

Considere:

* testes unitários;
* testes de integração;
* Testcontainers;
* testes de contrato;
* testes de persistência;
* testes de API.

Não execute somente o teste recém-criado se alterações puderem impactar comportamento existente.

Quando possível, execute também o conjunto completo do módulo afetado.

---

# 5. Avalie os testes

Não considere apenas se os testes passaram.

Verifique também:

* os testes realmente validam o comportamento relevante?
* existe assertion significativa?
* o teste depende excessivamente da implementação?
* foi utilizado mock onde integração real seria mais adequada?
* cenários de erro relevantes foram considerados?
* existem testes duplicados sem valor?

Não altere testes apenas para fazer uma implementação incorreta passar.

---

# 6. Verifique regras de negócio

Para mudanças de domínio, valide se:

* invariantes continuam protegidas;
* regras financeiras permanecem determinísticas;
* estados inválidos não foram permitidos;
* validações estão na camada adequada;
* comportamento de erro é previsível.

Não aceite lógica financeira crítica baseada em prompt, LLM ou Agent.

---

# 7. Verifique persistência

Quando houver banco de dados, revise:

* migration;
* constraints;
* nullable;
* unique;
* foreign keys;
* índices;
* tipos de dados;
* transações.

Questione:

> A integridade está protegida apenas na aplicação quando também deveria existir proteção no banco?

Quando houver alteração de migration já aplicada, prefira nova migration em vez de editar histórico, salvo contexto explicitamente local e descartável.

---

# 8. Verifique concorrência e idempotência

Quando relevantes, valide cenários como:

* duas requisições simultâneas;
* repetição da mesma chamada;
* retry após timeout;
* redelivery de mensagem;
* processamento duplicado.

Verifique se a proteção funciona com múltiplas instâncias da aplicação quando esse cenário for relevante.

Não confie apenas em estruturas em memória para exclusão distribuída.

---

# 9. Verifique integrações

Para chamadas entre serviços, revise:

* endpoint;
* contrato;
* timeout;
* tratamento de erro;
* status HTTP;
* serialização;
* observabilidade;
* idempotência quando necessária.

Se retry estiver presente, valide se a operação pode ser repetida com segurança.

Se circuit breaker estiver presente, valide o comportamento quando ele abrir.

---

# 10. Verifique eventos

Quando Kafka ou eventos estiverem envolvidos, valide:

* nome do evento;
* payload;
* chave de partição;
* ordering;
* duplicidade;
* consumer idempotente;
* retry;
* DLQ;
* compatibilidade de schema.

Quando houver persistência + publicação, confirme que não foi criado dual write inseguro.

---

# 11. Verifique API

Para endpoints REST, revise:

* método HTTP;
* URI;
* request;
* response;
* códigos HTTP;
* validação;
* erros;
* campos obrigatórios;
* compatibilidade.

Controllers não devem concentrar regras de negócio.

Evite expor entidades JPA diretamente como contrato externo sem decisão explícita.

---

# 12. Verifique dependências

Se alguma dependência foi adicionada:

* confirme que ela é utilizada;
* confirme que resolve problema real;
* verifique se a plataforma já não oferecia solução suficiente;
* verifique compatibilidade com a stack;
* evite dependências transitivas desnecessárias.

Não mantenha dependência adicionada durante experimentação se ela deixou de ser necessária.

---

# 13. Verifique segurança

Procure por:

* tokens;
* API keys;
* senhas;
* secrets;
* connection strings sensíveis;
* dados pessoais;
* dados financeiros em logs.

Nunca permita secrets versionados.

Quando necessário, use variáveis de ambiente e `.env.example`.

---

# 14. Verifique arquivos indevidos

Use:

```bash
git status
```

Garanta que arquivos gerados não estejam sendo adicionados.

Exemplos que não devem ser versionados:

```text
.gradle/
build/
out/
.idea/
*.class
.env
logs/
```

O Gradle Wrapper deve continuar versionado:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

---

# 15. Verifique formatação e warnings

Avalie:

* imports não utilizados;
* código morto;
* warnings relevantes;
* nomes inconsistentes;
* comentários obsoletos;
* TODOs adicionados incidentalmente.

Não faça refatoração ampla apenas para eliminar warning não relacionado.

---

# 16. Revise complexidade

Depois que tudo estiver funcional, pergunte:

* existe abstração desnecessária?
* existe classe sem responsabilidade real?
* existe interface sem fronteira justificável?
* existe wrapper que apenas encaminha chamada?
* existe configuração mais complexa que o necessário?
* alguma dependência pode ser removida?

Quando Ponytail estiver disponível, este é um bom momento para utilizá-lo.

Ponytail deve simplificar sem destruir decisões arquiteturais legítimas.

---

# 17. Compare implementação com estratégia

Se `implementation-strategy` foi utilizada, compare:

```text
planejado
vs.
implementado
```

Diferenças são permitidas quando novas informações surgirem.

Quando isso acontecer, explique:

* o que mudou;
* por que mudou;
* impacto da decisão.

Não deixe diferenças arquiteturais significativas sem explicação.

---

# 18. Aderência ao AGENTS.md

Antes de concluir, confirme:

* solução simples suficiente;
* ausência de overengineering;
* domínio em português quando apropriado;
* stack preservada;
* abstrações justificadas;
* regras financeiras determinísticas;
* nenhuma tecnologia antecipada sem necessidade;
* integração externa tratada como fronteira real;
* ausência de mudanças Git destrutivas.

---

# 19. Não esconda falhas

Se alguma verificação falhar:

* reporte a falha;
* explique a causa conhecida;
* corrija quando estiver dentro do escopo;
* execute novamente a verificação relevante.

Nunca diga:

> tudo funcionando

quando o build ou testes não foram executados.

Quando não for possível executar uma verificação, diga explicitamente:

```text
Não verificado:
<item>

Motivo:
<razão>
```

---

# 20. Formato da saída final

Ao concluir uma implementação, reporte de forma curta:

## Alterações

O que foi implementado.

## Verificação

Exemplo:

```text
- build: OK
- testes unitários: 18/18
- testes de integração: 4/4
- git diff revisado
- arquivos gerados: nenhum
```

## Decisões relevantes

Somente decisões que mereçam registro.

## Pendências

Liste apenas pendências reais.

Se não houver:

```text
Pendências: nenhuma.
```

---

# 21. Git

Esta skill pode utilizar comandos Git de leitura.

Não faça automaticamente:

```text
git commit
git push
git merge
git rebase
git reset
git clean
```

Quando a alteração estiver pronta, apenas sugira uma mensagem de commit.

Exemplo:

```text
feat: adiciona persistencia de duplicatas
```

O usuário decide quando realizar o commit.

---

# 22. Critério final

Uma mudança está pronta quando:

```text
comportamento correto
+
testes adequados
+
build funcionando
+
diff coerente
+
escopo controlado
+
arquitetura preservada
+
nenhum artefato indevido
```

Código escrito não é sinônimo de tarefa concluída.

Código verificado é o critério.
