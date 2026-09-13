# check-remote-encoding.ps1 - fail if any remote text file is not clean UTF-8/ASCII.
#
# WHY THIS EXISTS
#   On 2026-09-13 the GitHub repo Zhonz/Zhonz-s-More-Enchantments was found with its
#   ENTIRE main branch stored as UTF-16LE: every source file had a FF FE BOM and
#   thousands of NUL bytes. Git therefore treated every file as binary, diffs and
#   blame were useless, and a normal checkout produced garbage. The damage was
#   repaired on 2026-09-13 (clean UTF-8 history pushed), but the root cause was never
#   identified - the broken commits entered the remote from outside this checkout
#   (their SHAs do not exist locally). A writer that silently re-encodes text to
#   UTF-16 (Windows PowerShell 5.1 redirects / Set-Content default) will do it again.
#
# WHAT IT DOES
#   Reads the remote copy of every tracked text-ish file and rejects any that contain
#   a NUL byte (UTF-16) or a UTF-16 BOM. Run it after every push, and after anything
#   that writes to the repository.
#
# USAGE
#   powershell -ExecutionPolicy Bypass -File tools/check-remote-encoding.ps1
#   powershell ... -File tools/check-remote-encoding.ps1 -Ref origin/main
#   powershell ... -File tools/check-remote-encoding.ps1 -All    # every commit on origin/main

param(
  [string]$Ref = "origin/main",
  [switch]$All
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Extensions we expect to be plain text (git tracks these as text upstream).
$textExt = @(".java", ".json", ".md", ".gradle", ".properties", ".toml", ".yml", ".yaml",
             ".txt", ".xml", ".cfg", ".js", ".mjs", ".ps1", ".sh", ".gitignore")
$explicit = @(".gitignore", ".gitattributes")

if (-not $All) {
  $paths = git ls-tree -r --name-only $Ref
} else {
  # every file that ever appeared on the ref
  $paths = git log --pretty=format: --name-only $Ref | Sort-Object -Unique | Where-Object { $_ }
}

$bad = @()
$checked = 0
foreach ($p in $paths) {
  $isText = ($explicit -contains (Split-Path $p -Leaf)) -or ($textExt -contains [System.IO.Path]::GetExtension($p))
  if (-not $isText) { continue }

  $tmp = Join-Path $env:TEMP ("enc-check-" + [guid]::NewGuid().ToString("N") + ".bin")
  $args = 'show "' + $Ref + ':' + $p + '"'
  cmd /c ("git " + $args + ' > "' + $tmp + '"') 2>$null
  if (-not (Test-Path $tmp)) { continue }

  $b = [System.IO.File]::ReadAllBytes($tmp)
  Remove-Item $tmp -Force -ErrorAction SilentlyContinue
  $checked++

  $nul = 0
  foreach ($x in $b) { if ($x -eq 0) { $nul++ } }
  # FF FE = UTF-16LE BOM, FE FF = UTF-16BE BOM
  $bom = ($b.Length -ge 2) -and ((($b[0] -eq 0xFF) -and ($b[1] -eq 0xFE)) -or (($b[0] -eq 0xFE) -and ($b[1] -eq 0xFF)))

  if ($nul -gt 0 -or $bom) {
    $bad += [pscustomobject]@{ path = $p; bytes = $b.Length; nul = $nul; bom = $bom }
  }
}

Write-Output ("checked {0} text files at {1}" -f $checked, $Ref)
if ($bad.Count -eq 0) {
  Write-Output "OK: no NUL bytes and no UTF-16 BOM - repository encoding is clean"
  exit 0
}

Write-Output ""
Write-Output ("FAIL: {0} file(s) are NOT clean text (UTF-16 / NUL bytes detected):" -f $bad.Count)
$bad | Sort-Object path | ForEach-Object {
  Write-Output ("  {0,-70} bytes={1,-8} NUL={2,-7} BOM={3}" -f $_.path, $_.bytes, $_.nul, $_.bom)
}
Write-Output ""
Write-Output "A file with a UTF-16 BOM / NUL bytes means something rewrote it as UTF-16."
Write-Output "Typical culprit on Windows: PowerShell 5.1 '>' redirection or Set-Content without"
Write-Output "-Encoding, which default to UTF-16LE. Re-commit those files as UTF-8 (no BOM)."
exit 1
