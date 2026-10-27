# Roteiro de testes manuais — MS-AV-02

Execute **depois** de subir `docker compose up -d --build` e substituir as senhas em `.env`. No PowerShell:

```powershell
$base = 'http://localhost:8080'
$senhaManutencao = 'SENHA_DO_ENV_PARA_MANUTENCAO'
$senhaAdmin = 'SENHA_DO_ENV_PARA_ADMIN'
function Auth($usuario, $senha) {
  $texto = "${usuario}:${senha}"
  $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($texto))
  return @{ Authorization = "Basic $b64" }
}
$manutencao = Auth 'manutencao' $senhaManutencao
$admin = Auth 'admin' $senhaAdmin
function Json($dados) { return ($dados | ConvertTo-Json -Compress) }
```

## 1. Criar e listar usuários

```powershell
Invoke-RestMethod "$base/" -Method Post -Headers $manutencao -ContentType 'application/json' -Body (Json @{username='teste2';senha='SenhaInicial#2026'})
Invoke-RestMethod "$base/" -Method Get -Headers $manutencao
```

**Esperado:** cadastro `201` (PowerShell exibe corpo), listagem `200`; a senha **não** aparece no JSON.

## 2. Login válido

```powershell
Invoke-RestMethod "$base/login" -Method Post -ContentType 'application/json' -Body (Json @{username='teste2';senha='SenhaInicial#2026'})
```

**Esperado:** `200`. Verifique `app_logins_valid_total` em `http://localhost:8080/actuator/prometheus`.

## 3. Login inválido, bloqueio e desbloqueio

Execute o comando de senha errada **duas vezes**:

```powershell
try {
  Invoke-RestMethod "$base/login" -Method Post -ContentType 'application/json' -Body (Json @{username='teste2';senha='errada'})
} catch { Write-Host "Status HTTP:" ([int]$_.Exception.Response.StatusCode) }
```

**Esperado:** `401` nas duas chamadas. `app_logins_invalid_total` aumenta, e `app_users_blocked` sobe para pelo menos `1`.

Tente com a senha correta: ainda deve dar `401` enquanto bloqueado.

```powershell
try {
  Invoke-RestMethod "$base/login" -Method Post -ContentType 'application/json' -Body (Json @{username='teste2';senha='SenhaInicial#2026'})
} catch { Write-Host "Status HTTP:" ([int]$_.Exception.Response.StatusCode) }
```

Agora desbloqueie como admin:

```powershell
Invoke-RestMethod "$base/unlock/teste2" -Method Put -Headers $admin
Invoke-RestMethod "$base/login" -Method Post -ContentType 'application/json' -Body (Json @{username='teste2';senha='SenhaInicial#2026'})
```

**Esperado:** ambas as chamadas `200`. O gauge `app_users_blocked` volta a cair.

## 4. Troca de senha pública

```powershell
Invoke-RestMethod "$base/trocarsenha/teste2" -Method Put -ContentType 'application/json' -Body (Json @{atual='SenhaInicial#2026';nova='SenhaNova#2026'})
Invoke-RestMethod "$base/login" -Method Post -ContentType 'application/json' -Body (Json @{username='teste2';senha='SenhaNova#2026'})
```

**Esperado:** `200` em ambas as chamadas.

## 5. Restrições por perfil

```powershell
# Falta de role: MANUTENCAO nao pode desbloquear (403)
try { Invoke-RestMethod "$base/unlock/teste2" -Method Put -Headers $manutencao } catch { Write-Host ([int]$_.Exception.Response.StatusCode) }

# Falta de role: ADMIN nao pode listar (403)
try { Invoke-RestMethod "$base/" -Method Get -Headers $admin } catch { Write-Host ([int]$_.Exception.Response.StatusCode) }

# Sem credencial: GET / retorna 401
try { Invoke-RestMethod "$base/" -Method Get } catch { Write-Host ([int]$_.Exception.Response.StatusCode) }
```

## 6. Coletar o print e exportar

No Grafana (`http://localhost:3000`), abra **Dashboards > MS-AV-02 > MS-AV-02 | Seguranca e Observabilidade**. Confirme os números dos três cartões. Salve um print em `docs/dashboard-grafana.png`. Depois use **Export > Export as code > Classic** e salve o JSON como `docs/dashboard-exportado.json`. Faça commit dos dois arquivos no GitHub.

## 7. Resultado de testes automatizados

```powershell
mvn clean test
```

Os relatórios Surefire ficam em `target/surefire-reports/`. Verifique os resultados antes de enviar. Se a Action no GitHub ficar vermelha, corrija os testes antes de entregar.
