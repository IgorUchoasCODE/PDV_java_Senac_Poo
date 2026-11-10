$content = Get-Content "target\cp.txt" -Raw
$cpStr = $content.Trim()
if ($cpStr.StartsWith('-cp ')) {
    $cpStr = $cpStr.Substring(4).Trim().Trim('"')
}
& java "-Dfile.encoding=UTF-8" -cp "$cpStr;target/classes" br.com.senac.application.ChatBotTerminal
