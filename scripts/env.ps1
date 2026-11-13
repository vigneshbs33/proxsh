# Sets up the build tools for this terminal. Everything lives on the D drive.
#
#   . .\scripts\env.ps1
#   gradle :core:test
#
# Change PROXSH_TOOLS if your tools are somewhere else.

$tools = if ($env:PROXSH_TOOLS) { $env:PROXSH_TOOLS } else { "D:\vignesh\tools" }
$cache = if ($env:PROXSH_CACHE) { $env:PROXSH_CACHE } else { "D:\vignesh\.cache" }

$env:JAVA_HOME        = Join-Path $tools "jdk-21"
$env:GRADLE_USER_HOME = Join-Path $cache "gradle"
$env:ANDROID_HOME     = Join-Path $tools "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:TEMP             = Join-Path $cache "tmp"
$env:TMP              = $env:TEMP
New-Item -ItemType Directory -Force $env:TEMP | Out-Null

$env:Path = (Join-Path $tools "gradle-9.9.1\bin") + ";" + (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path

Write-Host "Java:   $env:JAVA_HOME"
Write-Host "Gradle: $env:GRADLE_USER_HOME"
Write-Host "Temp:   $env:TEMP"
