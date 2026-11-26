param(
    [Parameter(Mandatory)][string]$BaseVersion,
    [Parameter(Mandatory)][long]$BaseCode,
    [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Tags,
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Releases
)

$ErrorActionPreference = 'Stop'
$versionPattern = '(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'
if ($BaseVersion -cnotmatch "^$versionPattern$" -or $BaseCode -lt 1 -or $BaseCode -ge 2100000000) {
    throw 'Invalid baseline release version or exhausted Android versionCode.'
}
$baseline = [version]$BaseVersion
$patch = $baseline.Build
$code = $BaseCode
foreach ($tag in @($Tags) + @($Releases | ForEach-Object { $_.tag_name })) {
    if ($tag -cnotmatch "^v($versionPattern)$") { continue }
    $version = [version]$Matches[1]
    if ($version.Major -gt $baseline.Major -or
            ($version.Major -eq $baseline.Major -and $version.Minor -gt $baseline.Minor)) {
        throw 'A newer major/minor release series exists. Update the baseline in app/build.gradle before publishing.'
    }
    if ($version.Major -eq $baseline.Major -and $version.Minor -eq $baseline.Minor) {
        $patch = [Math]::Max($patch, $version.Build)
    }
}
foreach ($release in $Releases) {
    if ($release.tag_name -cnotmatch "^v($versionPattern)$") { continue }
    $version = [version]$Matches[1]
    $codes = [regex]::Matches([string]$release.body, '(?m)^Android versionCode: ([1-9][0-9]*)\r?$')
    if ($codes.Count -eq 0 -and $version -lt $baseline) { continue }
    if ($codes.Count -ne 1) {
        throw "Missing or ambiguous Android versionCode in release $($release.tag_name). Inspect release history before publishing."
    }
    $previousCode = [long]$codes[0].Groups[1].Value
    if ($previousCode -ge 2100000000) { throw 'Android versionCode is exhausted.' }
    $code = [Math]::Max($code, $previousCode)
}
if ($patch -eq [int]::MaxValue) { throw 'Patch version is exhausted.' }
[pscustomobject]@{
    Version = "$($baseline.Major).$($baseline.Minor).$($patch + 1)"
    Code = $code + 1
}
