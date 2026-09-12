# release-v1.3.0.ps1 - push commits and publish GitHub Release v1.3.0 with the three platform jars.
# NOTE: keep this file ASCII-only - Windows PowerShell 5.1 reads .ps1 as ANSI, non-ASCII breaks parsing.
# Wider access is needed: the sandbox blocks git/curl TLS to GitHub (schannel SEC_E_NO_CREDENTIALS).
$ErrorActionPreference = "Stop"
$repo = "Zhonz/Zhonz-s-More-Enchantments"
$tag  = "v1.3.0"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- 1. read the git OAuth token from Windows Credential Manager (never printed in full) ---
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
if (-not $tok) { throw "no token found at git:https://github.com in Credential Manager" }
Write-Output ("[1/5] token loaded (len={0}, prefix={1})" -f $tok.Length, $tok.Substring(0, 4))

# --- 2. push local commits ---
$env:GIT_TERMINAL_PROMPT = "0"
$url = "https://Zhonz:$tok@github.com/$repo.git"
# git writes progress to stderr, which PowerShell would otherwise turn into a terminating error
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$pushOut = git -c credential.helper= push $url main 2>&1
$pushCode = $LASTEXITCODE
$ErrorActionPreference = $prevEap
$pushOut | ForEach-Object { Write-Output ("  git: " + $_) }
if ($pushCode -ne 0) { throw "git push failed with exit $pushCode" }
Write-Output "[2/5] main pushed"

# --- 3. verify the three artifacts exist ---
$jars = @(
  "build/libs/zhonz_more_enchantments-1.3.0.jar",
  "platforms/1.20.1-forge/build/libs/zhonz-more-enchantments-1.20.1-forge-1.3.0.jar",
  "platforms/1.20.1-neoforge/build/libs/zhonz-more-enchantments-1.20.1-neoforge-1.3.0.jar"
)
foreach ($j in $jars) {
  if (-not (Test-Path $j)) { throw "missing artifact: $j" }
  Write-Output ("[3/5] {0} ({1:N1} KB)" -f (Split-Path $j -Leaf), ((Get-Item $j).Length / 1KB))
}

# --- 4. create the release (reuse it when the tag already exists) ---
$notesPath = Join-Path $root "tools/release-notes-v1.3.0.md"
if (-not (Test-Path $notesPath)) { throw "missing release notes: $notesPath" }
$body = [System.IO.File]::ReadAllText($notesPath, (New-Object System.Text.UTF8Encoding($false)))
# build the payload by hand so the UTF-8 markdown survives without BOM/encoding damage
$esc = $body.Replace('\', '\\').Replace('"', '\"').Replace("`r`n", '\n').Replace("`n", '\n')
$payload = '{"tag_name":"' + $tag + '","target_commitish":"main","name":"' + $tag + '","body":"' + $esc + '","draft":false,"prerelease":false}'
$payloadPath = Join-Path $env:TEMP "rel-payload.json"
[System.IO.File]::WriteAllText($payloadPath, $payload, (New-Object System.Text.UTF8Encoding($false)))

$apiHeaders = @("-H", "Authorization: Bearer $tok", "-H", "User-Agent: dsh-agent", "-H", "Accept: application/vnd.github+json")
$existing = curl.exe -s @apiHeaders "https://api.github.com/repos/$repo/releases/tags/$tag"
$relId = $null
try { $relId = ($existing | ConvertFrom-Json).id } catch { }
if ($relId) {
  Write-Output "[4/5] release $tag exists (id=$relId), reusing"
} else {
  $created = curl.exe -s -X POST @apiHeaders -H "Content-Type: application/json" --data-binary "@$payloadPath" "https://api.github.com/repos/$repo/releases"
  try { $relId = ($created | ConvertFrom-Json).id } catch { }
  if (-not $relId) { throw "release creation failed: $created" }
  Write-Output "[4/5] release $tag created (id=$relId)"
}

# --- 5. upload the three jars ---
foreach ($j in $jars) {
  $name = Split-Path $j -Leaf
  $up = curl.exe -s -X POST @apiHeaders -H "Content-Type: application/java-archive" --data-binary "@$j" "https://uploads.github.com/repos/$repo/releases/$relId/assets?name=$name"
  $state = "?"
  try { $state = ($up | ConvertFrom-Json).state } catch { $state = "parse-error: $up" }
  Write-Output ("[5/5] upload {0} -> state={1}" -f $name, $state)
}

# --- summary ---
$final = curl.exe -s @apiHeaders "https://api.github.com/repos/$repo/releases/tags/$tag" | ConvertFrom-Json
Write-Output "RELEASE_URL=$($final.html_url)"
Write-Output "RELEASE_NAME=$($final.name)"
foreach ($a in $final.assets) { Write-Output ("ASSET {0} {1} bytes downloads={2}" -f $a.name, $a.size, $a.download_count) }
