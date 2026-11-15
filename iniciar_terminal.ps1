$utf8SemBom = New-Object System.Text.UTF8Encoding $false
[Console]::InputEncoding = $utf8SemBom
[Console]::OutputEncoding = $utf8SemBom
$OutputEncoding = $utf8SemBom

$content = Get-Content "target\cp.txt" -Raw
$cpStr = $content.Trim() -replace 'C:\\Users\\[^\\]+\\.m2\\repository', "$env:USERPROFILE\.m2\repository"
if ($cpStr.StartsWith('-cp ')) {
    $cpStr = $cpStr.Substring(4).Trim().Trim('"')
}

$jfxDir = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
if (Test-Path $jfxDir) {
    $jfxJars = Get-ChildItem -Path $jfxDir -Recurse -Filter "*-win.jar" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    foreach ($jar in $jfxJars) {
        if (-not $cpStr.Contains($jar)) {
            $cpStr = "$cpStr;$jar"
        }
    }
}

$targetClass = "target\classes\br\com\senac\application\ChatBotTerminal.class"
$terminalClass = "target\classes\br\com\senac\application\terminal\EditorTerminalInterativo.class"
$needsCompile = (-not (Test-Path $targetClass)) -or (-not (Test-Path $terminalClass))
if (-not $needsCompile) {
    $classTime = (Get-Item $targetClass).LastWriteTime
    $newerSrc = Get-ChildItem -Path "src\main\java\br\com\senac\application" -Recurse -Filter "*.java" | Where-Object { $_.LastWriteTime -gt $classTime }
    if ($newerSrc) {
        $needsCompile = $true
    }
}

if ($needsCompile) {
    if (-not (Test-Path "target\classes")) {
        New-Item -ItemType Directory -Path "target\classes" -Force | Out-Null
    }
    $appSources = @("src\main\java\br\com\senac\application\ChatBotTerminal.java") +
        (Get-ChildItem -Path "src\main\java\br\com\senac\application\terminal" -Filter "*.java" | Select-Object -ExpandProperty FullName)
    & javac -encoding UTF-8 -d "target\classes" -cp "$cpStr;target\classes" -sourcepath "src\main\java" @appSources
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($MyInvocation.ExpectingInput) {
    $input | & java "-Dfile.encoding=UTF-8" -cp "$cpStr;target/classes" br.com.senac.application.ChatBotTerminal
} else {
    & java "-Dfile.encoding=UTF-8" -cp "$cpStr;target/classes" br.com.senac.application.ChatBotTerminal
}
