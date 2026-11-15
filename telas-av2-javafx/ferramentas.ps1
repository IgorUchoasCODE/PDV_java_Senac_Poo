function Get-JavaTool([string]$Tool) {
    $candidatos = @()
    if ($env:JAVA_HOME) { $candidatos += Join-Path $env:JAVA_HOME "bin/$Tool.exe" }
    $pastaJava = Join-Path $env:ProgramFiles 'Java'
    if (Test-Path $pastaJava) {
        $candidatos += Get-ChildItem $pastaJava -Directory -Filter 'jdk*' | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName "bin/$Tool.exe" }
    }
    $command = Get-Command "$Tool.exe" -ErrorAction SilentlyContinue
    if ($command) { $candidatos += $command.Source }
    foreach ($candidato in ($candidatos | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidato)) { continue }
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $candidato
        $info.Arguments = '-version'
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        try {
            $processo = [System.Diagnostics.Process]::Start($info)
            if (-not $processo.WaitForExit(5000)) {
                $processo.Kill()
                continue
            }
            $versao = $processo.StandardOutput.ReadToEnd() + $processo.StandardError.ReadToEnd()
            if ($processo.ExitCode -eq 0 -and $versao -match '(?:version\s+"?|javac\s+)(\d+)(?:\.|"|\s)') {
                if ([int]$Matches[1] -ge 21) { return $candidato }
            }
        } catch { continue }
    }
    throw "Nao foi encontrado $Tool do Java 21 ou superior. Instale JDK 21+ e configure JAVA_HOME ou PATH."
}
