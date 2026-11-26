$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'Get-ReleaseVersion.ps1'

function Assert-Version($tags, $releases, $version, $code) {
    $actual = & $script -BaseVersion '1.1.7' -BaseCode 10 -Tags $tags -Releases $releases
    if ($actual.Version -cne $version -or $actual.Code -ne $code) {
        throw "Expected $version ($code), got $($actual.Version) ($($actual.Code))."
    }
}

function Assert-Rejected($tags, $releases, $message, $baseCode = 10, $baseVersion = '1.1.7') {
    try {
        & $script -BaseVersion $baseVersion -BaseCode $baseCode -Tags $tags -Releases $releases | Out-Null
    } catch {
        if ($_.Exception.Message -notlike "*$message*") { throw }
        return
    }
    throw 'Expected release selection to fail.'
}

Assert-Version @() @() '1.1.8' 11
Assert-Version @('v1.0.0', 'v1.1.6', 'unrelated') @(
    @{ tag_name = 'v1.0.0'; body = 'Legacy release without versionCode' }
    @{ tag_name = 'v1.1.6'; body = 'Android versionCode: 9' }
) '1.1.8' 11
Assert-Version @('v1.1.9', 'v1.1.10', 'v1.1.7') @(
    @{ tag_name = 'v1.1.10'; body = "Reviewed source: abc`r`nAndroid versionCode: 21`r`n"; draft = $true }
    @{ tag_name = 'v1.1.7'; body = 'Android versionCode: 10' }
) '1.1.11' 22
Assert-Version @('v1.1.8') @(
    @{ tag_name = 'v1.1.8'; body = 'Android versionCode: 11' }
) '1.1.9' 12
$lastCode = & $script -BaseVersion '1.1.7' -BaseCode 2099999999 -Tags @() -Releases @()
if ($lastCode.Code -ne 2100000000) { throw 'The final valid Android versionCode must remain available.' }
Assert-Rejected @('v1.2.0') @() 'newer major/minor'
Assert-Rejected @('v2.0.0') @() 'newer major/minor'
Assert-Rejected @() @(@{ tag_name = 'v1.1.8'; body = 'No version code' }) 'Missing or ambiguous'
Assert-Rejected @() @(@{ tag_name = 'v1.1.8'; body = "Android versionCode: 11`nAndroid versionCode: 12" }) 'Missing or ambiguous'
Assert-Rejected @() @(@{ tag_name = 'v1.1.8'; body = 'Android versionCode: 2100000000' }) 'exhausted'
Assert-Rejected @() @() 'exhausted' 2100000000
Assert-Rejected @('v1.1.2147483647') @() 'Patch version is exhausted'
Assert-Rejected @() @() 'Invalid baseline' 10 '1.1.07'
Write-Output 'Release version selection checks passed.'
