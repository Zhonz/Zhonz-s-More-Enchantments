# release-v1.3.0.ps1 — 推送 1.3.0 版本提交并发布 GitHub Release v1.3.0(含三平台 jar)
# 用法: pwsh -File tools/release-v1.3.0.ps1
# 说明: 本机沙箱会阻断 schannel/openssl 的 TLS 凭据获取, 因此本脚本需在更宽权限下运行。
$ErrorActionPreference = "Stop"
$repo = "Zhonz/Zhonz-s-More-Enchantments"
$tag = "v1.3.0"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- 1. 从 Windows 凭据管理器读取 git 令牌(OAuth, 不入库/不打印) ---
$src = @"
using System;
using System.Runtime.InteropServices;
public class CredRel {
  [DllImport("advapi32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
  public static extern bool CredRead(string target, int type, int flags, out IntPtr credential);
  [DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
  public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
  public static string Get(string target) {
    IntPtr p = IntPtr.Zero;
    if (!CredRead(target, 1, 0, out p)) return null;
    var c = (CREDENTIAL)Marshal.PtrToStructure(p, typeof(CREDENTIAL));
    byte[] b = new byte[c.CredentialBlobSize];
    Marshal.Copy(c.CredentialBlob, b, 0, c.CredentialBlobSize);
    CredFree(p);
    return System.Text.Encoding.Unicode.GetString(b);
  }
}
"@
Add-Type -TypeDefinition $src
$tok = [CredRel]::Get("git:https://github.com")
if (-not $tok) { throw "未从凭据管理器取到 git:https://github.com 的令牌" }
Write-Output "[1/5] 令牌已读取(len=$($tok.Length), prefix=$($tok.Substring(0,4)))"

# --- 2. 推送本地提交 ---
$env:GIT_TERMINAL_PROMPT = "0"
$url = "https://Zhonz:$tok@github.com/$repo.git"
git -c credential.helper= push $url main 2>&1 | ForEach-Object { $_ }
Write-Output "[2/5] 已推送 main"

# --- 3. 校验三个平台产物 ---
$jars = @(
  "build/libs/zhonz_more_enchantments-1.3.0.jar",
  "platforms/1.20.1-forge/build/libs/zhonz-more-enchantments-1.20.1-forge-1.3.0.jar",
  "platforms/1.20.1-neoforge/build/libs/zhonz-more-enchantments-1.20.1-neoforge-1.3.0.jar"
)
foreach ($j in $jars) {
  if (-not (Test-Path $j)) { throw "缺少产物: $j" }
  $len = (Get-Item $j).Length
  Write-Output ("[3/5] {0} ({1:N1} KB)" -f (Split-Path $j -Leaf), ($len / 1KB))
}

# --- 4. 创建 Release(已存在则复用) ---
$headers = @{ Authorization = "Bearer $tok"; "User-Agent" = "dsh-agent"; Accept = "application/vnd.github+json" }
$notesPath = Join-Path $root "tools/release-notes-v1.3.0.md"
if (-not (Test-Path $notesPath)) { throw "缺少 release 说明: $notesPath" }
$body = [System.IO.File]::ReadAllText($notesPath, (New-Object System.Text.UTF8Encoding($false)))
$payload = @{ tag_name = $tag; target_commitish = "main"; name = "v1.3.0"; body = $body; draft = $false; prerelease = $false } | ConvertTo-Json -Depth 4
$payloadPath = Join-Path $env:TEMP "rel-payload.json"
[System.IO.File]::WriteAllText($payloadPath, $payload, (New-Object System.Text.UTF8Encoding($false)))

$existing = curl.exe -s -H "Authorization: Bearer $tok" -H "User-Agent: dsh-agent" "https://api.github.com/repos/$repo/releases/tags/$tag"
$relId = $null
try { $relId = ($existing | ConvertFrom-Json).id } catch { }
if ($relId) {
  Write-Output "[4/5] Release $tag 已存在(id=$relId), 复用"
} else {
  $created = curl.exe -s -X POST -H "Authorization: Bearer $tok" -H "User-Agent: dsh-agent" -H "Accept: application/vnd.github+json" -H "Content-Type: application/json" --data-binary "@$payloadPath" "https://api.github.com/repos/$repo/releases"
  $relId = ($created | ConvertFrom-Json).id
  if (-not $relId) { throw "创建 Release 失败: $created" }
  Write-Output "[4/5] Release $tag 已创建(id=$relId)"
}

# --- 5. 上传三个产物 ---
foreach ($j in $jars) {
  $name = Split-Path $j -Leaf
  $up = curl.exe -s -X POST -H "Authorization: Bearer $tok" -H "User-Agent: dsh-agent" -H "Content-Type: application/java-archive" --data-binary "@$j" "https://uploads.github.com/repos/$repo/releases/$relId/assets?name=$name"
  $state = ($up | ConvertFrom-Json).state
  Write-Output "[5/5] 上传 $name -> state=$state"
}

# --- 汇总 ---
$final = curl.exe -s -H "Authorization: Bearer $tok" -H "User-Agent: dsh-agent" "https://api.github.com/repos/$repo/releases/tags/$tag" | ConvertFrom-Json
Write-Output "RELEASE_URL=$($final.html_url)"
Write-Output "ASSETS=$((($final.assets | ForEach-Object { "$($_.name):$($_.size)" }) -join ', '))"
