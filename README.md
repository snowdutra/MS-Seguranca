# MS-AV-02 — Segurança e Observabilidade

Projeto em **Java 21 + Spring Boot 3.5 + Maven**, baseado no enunciado `MS-AV-02 - Segurança e Observabilidade`.

Implementa autenticação REST, autorização via `JdbcUserDetailsManager`, bloqueio após duas falhas consecutivas, troca de senha, métricas com Spring Actuator/Micrometer/Prometheus, Swagger/OpenAPI e dashboard Grafana provisionado automaticamente.

> **Status da execução:** este projeto contém testes de integração com JUnit/MockMvc, executados pelo comando `mvn test` ou pela ação do GitHub. Confira o resultado no seu ambiente antes de entregar. A imagem real do dashboard deve ser capturada após subir os containers; não há imagem de execução simulada no repositório.

## 1. Requisitos

- **Opção recomendada:** Docker Desktop com Docker Compose (inclui Java, Prometheus e Grafana no ambiente dos containers).
- **Opção manual:** JDK 21 e Apache Maven 3.9+; Docker Desktop é necessário também para os serviços Prometheus e Grafana, caso queira executar o dashboard com `compose.yml`.

## 2. Executar o sistema inteiro no Windows

No **PowerShell**, dentro da pasta deste repositório:

```powershell
Copy-Item .env.example .env
notepad .env
```

Substitua as três senhas de exemplo por senhas próprias. **Não envie `.env` para o GitHub** (está no `.gitignore`). A configuração `APP_ADMIN_PASSWORD` e `APP_MANUTENCAO_PASSWORD` serve para criar os perfis iniciais **somente se ainda não existirem** no banco. Depois de alterar `.env`, reiniciar não altera senhas previamente cadastradas.

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

Quando a aplicação estiver pronta, os endereços locais são:

| Componente | URL |
| --- | --- |
| API REST | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Métricas Prometheus | http://localhost:8080/actuator/prometheus |
| Interface Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

No Grafana, entre com `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` do `.env`. Em **Dashboards > MS-AV-02** abra **MS-AV-02 | Seguranca e Observabilidade**. A fonte Prometheus e o painel já são configurados pelos arquivos de `observability/`.

Para encerrar: `docker compose down` (preserva os volumes). **Atenção:** `docker compose down -v` também apaga usuários, contagens persistidas no H2 e dados do Grafana.

## 3. Endpoints exigidos

Os caminhos seguem o **enunciado literalmente** (inclusive a raiz `/`). `{userid}` é o campo `username`, pois o SQL usa `username` como chave primária.

| Método | Rota | Acesso | Corpo | Resposta de sucesso |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | Público | `{"username":"...","senha":"..."}` | `200` |
| `PUT` | `/trocarsenha/{userid}` | Público, validando senha atual | `{"atual":"...","nova":"..."}` | `200` |
| `POST` | `/` | `ROLE_MANUTENCAO` | `{"username":"...","senha":"..."}` | `201` |
| `PUT` | `/unlock/{userid}` | `ROLE_ADMIN` | sem corpo | `200` |
| `GET` | `/` | `ROLE_MANUTENCAO` | — | `200` |

`401`: senha inválida, usuário bloqueado, ou falta de autenticação em rota protegida. `403`: autenticado sem a role necessária. `404`: recurso/usuário não encontrado nas operações aplicáveis. `409`: usuário duplicado. `400`: dados inválidos.

Usuários de exemplo, inicializados no primeiro start:

| Usuário | Role | Uso |
| --- | --- | --- |
| `admin` | `ROLE_ADMIN` | desbloquear usuários |
| `manutencao` | `ROLE_MANUTENCAO` | cadastrar e listar usuários |
| usuários cadastrados | `ROLE_USUARIO` | efetuar login e trocar a própria senha mediante senha atual |

**Importante:** o endpoint `POST /login` verifica credenciais e devolve um resultado, **não emite um token**. As rotas protegidas utilizam **HTTP Basic** sobre o `JdbcUserDetailsManager`, porque é a autenticação mais direta que cumpre os requisitos de roles do enunciado. Em um sistema real, usaria HTTPS, proteção adicional contra força bruta, credenciais rotacionáveis e possivelmente OAuth2/OIDC conforme o contexto.

## 4. Teste manual rápido (PowerShell)

Com os containers ativos, defina a mesma senha que colocou em `APP_MANUTENCAO_PASSWORD`:

```powershell
$base = 'http://localhost:8080'
$senhaManutencao = 'COLOQUE_A_SENHA_DO_SEU_ENV'
$cred = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("manutencao:$senhaManutencao"))
$headers = @{ Authorization = "Basic $cred" }

# Cadastrar: deve retornar HTTP 201
Invoke-RestMethod -Uri "$base/" -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Body (@{ username='teste1'; senha='SenhaSegura#2026' } | ConvertTo-Json)

# Listar: deve retornar uma lista SEM senhas
Invoke-RestMethod -Uri "$base/" -Method Get -Headers $headers

# Login correto: HTTP 200
Invoke-RestMethod -Uri "$base/login" -Method Post `
  -ContentType 'application/json' `
  -Body (@{ username='teste1'; senha='SenhaSegura#2026' } | ConvertTo-Json)
```

Para verificar o **bloqueio após 2 erros**, use o exemplo em [`docs/TESTES-MANUAIS.md`](docs/TESTES-MANUAIS.md). Consulte as métricas em `http://localhost:8080/actuator/prometheus` e atualize o Grafana. Testes de login feitos diretamente no endpoint protegido via Basic Auth não alteram os contadores da rota `/login`; são mecanismos distintos.

## 5. Métricas e dashboard

| Métrica Prometheus | Tipo | Significado |
| --- | --- | --- |
| `app_logins_valid_total` | Counter | logins aceitos desde que a API iniciou |
| `app_logins_invalid_total` | Counter | logins recusados desde que a API iniciou |
| `app_users_blocked` | Gauge | usuários atualmente bloqueados, consultados no banco |

As contagens de logins são **contadores de processo** (reiniciam com a aplicação), como é comum em métricas Prometheus. O bloqueio de usuários e o número de falhas por conta ficam persistidos na tabela `login_attempts` do H2. O Prometheus mantém séries temporais no seu volume próprio.

O dashboard **Classic JSON**, pronto para importação/provisionamento, está em:

`observability/grafana/dashboards/dashboard-ms-av-02.json`

Ele contém três painéis de números e um gráfico de tendências. A fonte de dados Prometheus é provisionada por `observability/grafana/provisioning/datasources/prometheus.yml`.

### Evidências obrigatórias para a entrega

O professor pede **print real do dashboard e exportação em JSON**. Após os testes no Grafana:

1. Abra `http://localhost:3000` e selecione o dashboard.
2. Gere tráfego de login válido e inválido; bloqueie `teste1` com duas senhas incorretas.
3. Aguarde a coleta do Prometheus (configurada a cada 5 segundos) e confira os três indicadores.
4. Capture um **print real do Grafana** (no Windows use `Win + Shift + S`) e salve como **`docs/dashboard-grafana.png`**.
5. No Grafana: **Dashboards > abra o dashboard > Export > Export as code > Model: Classic > Download file**. Salve a exportação em **`docs/dashboard-exportado.json`**. O arquivo de provisionamento já é um JSON Classic, mas a exportação pela interface documenta o procedimento solicitado.
6. Execute `git add .`, faça o commit e confira no GitHub público que ambos os arquivos de evidência estão presentes.

> **Não suba um print fictício**. Os dois arquivos de evidência gerados depois da execução (`docs/dashboard-grafana.png` e `docs/dashboard-exportado.json`) precisam ser capturados no seu Grafana. Leia a seção de entrega antes do `git push`.

## 6. Executar apenas o Spring Boot com Maven

Tenha JDK 21 e Maven instalados, e, no **PowerShell**, configure as senhas de bootstrap:

```powershell
$env:APP_ADMIN_PASSWORD = 'SuaSenhaAdmin#2026'
$env:APP_MANUTENCAO_PASSWORD = 'SuaSenhaManutencao#2026'
mvn test
mvn spring-boot:run
```

A aplicação grava o H2 em `./data/seguranca-db.mv.db`, ignorado no Git. A inicialização executa `src/main/resources/schema.sql`. Com a aplicação Maven no host, a configuração de Prometheus no Compose (`app:8080`) aponta para o container, e não para a aplicação do host: para usar Grafana com Maven, execute o Compose ou adapte o alvo de scrape.

## Correcao da suite de testes

A classe de testes inclui `@AutoConfigureObservability` para habilitar o exportador
Prometheus no contexto de `@SpringBootTest` (Spring Boot 3.5). Sem essa anotacao,
o Spring Boot desativa registries de metricas externos durante testes, embora
o endpoint funcione com a aplicacao iniciada normalmente. A troca de senha
usa `JdbcUserDetailsManager.updateUser(UserDetails)` para persistir o novo hash.

## 7. Testes automatizados

```powershell
mvn clean test
mvn clean package
```

O arquivo `MsAv02IntegrationTest.java` contém testes HTTP com MockMvc cobrindo login válido/inválido, bloqueio na 2ª falha, desbloqueio exclusivo do admin, permissões da manutenção, usuário duplicado, troca de senha pública, BCrypt, métricas e OpenAPI.

Uma GitHub Action em `.github/workflows/testes.yml` também executa `mvn test` em push e pull request. **Consulte os resultados reais** do Maven e da Action: a inclusão de testes não equivale a uma execução bem-sucedida.

## 8. Estrutura principal

```text
src/main/java/br/com/msav02/
  config/          SecurityConfig, OpenApiConfig, BootstrapConfig
  controller/      UsuarioController
  dto/             objetos de requisicao e resposta
  exception/       respostas HTTP de erro
  repository/      consultas JDBC e controle de bloqueio
  service/         LoginService, UsuarioService
src/main/resources/
  schema.sql       usuarios, login_attempts, authorities
  application.yml
src/test/           testes de integracao (JUnit + MockMvc)
observability/
  prometheus/prometheus.yml
  grafana/provisioning/...
  grafana/dashboards/dashboard-ms-av-02.json
compose.yml         API + Prometheus + Grafana
Dockerfile          build Java 21/Maven
.env.example        variaveis sem credenciais reais
```

## 9. Observações técnicas sobre o enunciado

- O SQL copiado do professor traz uma vírgula antes de `)` em `login_attempts`; foi removida.
- O campo `usuarios.senha VARCHAR(10)` não comporta um hash BCrypt (`60` caracteres). Foi atualizado para `VARCHAR(100)` e toda senha é armazenada com BCrypt.
- `usuarios.enabled` foi acrescentado para compatibilidade com `JdbcUserDetailsManager`, assim como `authorities` para as roles.
- Usuários são armazenados **exclusivamente em `usuarios`**, e tanto `/login` quanto o Basic Auth usam essa tabela. O manager executa cadastro e recupera as roles do mesmo banco.
- Duas falhas **consecutivas** no `/login` bloqueiam a conta. O admin precisa desbloqueá-la; acertos anteriores à segunda falha zeram a contagem.
- `{userid}` corresponde a `username`, a chave primária definida no enunciado.
- `PUT /trocarsenha/{userid}` permanece público como exigido, mas exige a **senha atual correta**, evitando alteração arbitrária de credenciais.
- OAuth2 não é obrigatório entre os endpoints descritos na atividade. Basic Auth é mantido apenas para este exercício, em execução local. Em produção, não exponha endpoints de métricas sem proteção adequada.

## 10. GitHub e checklist de entrega

Crie um **repositório público vazio** no GitHub, por exemplo `ms-av-02-seguranca-observabilidade`, e execute dentro desta pasta:

```powershell
git init
git branch -M main
git add .
git commit -m "MS-AV-02: seguranca JDBC, login, bloqueio e observabilidade"
git remote add origin https://github.com/SEU_USUARIO/ms-av-02-seguranca-observabilidade.git
git push -u origin main
```

Antes de enviar o link ao professor, confirme:

- [ ] `mvn test` passa localmente **ou** a Action do GitHub passa.
- [ ] A API sobe e o Swagger abre.
- [ ] Bloqueio com 2 logins incorretos; desbloqueio exclusivo do `admin`.
- [ ] Métricas aparecem em `/actuator/prometheus` e no Grafana.
- [ ] `docs/dashboard-grafana.png` contém o **print real**.
- [ ] `docs/dashboard-exportado.json` contém a **exportação feita no Grafana**.
- [ ] `.env`, senhas reais, `data/*.mv.db` e `target/` **não** foram commitados.
- [ ] Repositório está **público**, com README e `compose.yml`.

**Fontes:** [enunciado oficial](https://github.com/esensato/ms-2026-02/blob/main/MS-AV-02-Seguran%C3%A7a-Observabilidade.md), [JdbcUserDetailsManager](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/jdbc.html), [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/).
