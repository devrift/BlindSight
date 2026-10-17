param(
    [string]$text,
    [string]$outPath
)
$voice = New-Object -ComObject SAPI.SpVoice
$stream = New-Object -ComObject SAPI.SpFileStream
# 22 = SAFT22kHz16BitMono
$format = New-Object -ComObject SAPI.SpAudioFormat
$format.Type = 22
$stream.Format = $format
$stream.Open($outPath, 3, $false)
$voice.AudioOutputStream = $stream
$voice.Speak($text)
$stream.Close()
