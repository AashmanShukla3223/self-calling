# Generates legacy launcher PNGs (Android 6-7) matching the adaptive icon:
# dark gradient bg + green return-loop arrow + white handset.
Add-Type -AssemblyName System.Drawing

$callPath = 'M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1v3.5c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.24,1.02l-2.2,2.2z'

function Add-PathData {
    param($gp, [string]$d, [double]$s, [double]$ox, [double]$oy)
    $ms = [regex]::Matches($d, '([MmLlCcHhVvZz])|(-?\d*\.?\d+(?:[eE][-+]?\d+)?)')
    $cx = 0.0; $cy = 0.0; $sx = 0.0; $sy = 0.0
    $i = 0
    while ($i -lt $ms.Count) {
        $t = $ms[$i].Value
        if ($t -cmatch '^[A-Za-z]$') {
            $i++
            $nums = @()
            while ($i -lt $ms.Count -and ($ms[$i].Value -cnotmatch '^[A-Za-z]$')) {
                $nums += [double]$ms[$i].Value
                $i++
            }
            $p = 0
            switch -CaseSensitive ($t) {
                'M' { while ($p -lt $nums.Count) { if ($p -eq 0) { $gp.StartFigure() }; $nx = $nums[$p]; $ny = $nums[$p + 1]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $nx * $s), ($oy + $ny * $s)); $cx = $nx; $cy = $ny; $sx = $cx; $sy = $cy; $p += 2 } }
                'm' { while ($p -lt $nums.Count) { if ($p -eq 0) { $gp.StartFigure() }; $nx = $cx + $nums[$p]; $ny = $cy + $nums[$p + 1]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $nx * $s), ($oy + $ny * $s)); $cx = $nx; $cy = $ny; $sx = $cx; $sy = $cy; $p += 2 } }
                'L' { while ($p -lt $nums.Count) { $nx = $nums[$p]; $ny = $nums[$p + 1]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $nx * $s), ($oy + $ny * $s)); $cx = $nx; $cy = $ny; $p += 2 } }
                'l' { while ($p -lt $nums.Count) { $nx = $cx + $nums[$p]; $ny = $cy + $nums[$p + 1]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $nx * $s), ($oy + $ny * $s)); $cx = $nx; $cy = $ny; $p += 2 } }
                'C' { while ($p -lt $nums.Count) { $c1x = $nums[$p]; $c1y = $nums[$p + 1]; $c2x = $nums[$p + 2]; $c2y = $nums[$p + 3]; $ex = $nums[$p + 4]; $ey = $nums[$p + 5]; $gp.AddBezier(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $c1x * $s), ($oy + $c1y * $s), ($ox + $c2x * $s), ($oy + $c2y * $s), ($ox + $ex * $s), ($oy + $ey * $s)); $cx = $ex; $cy = $ey; $p += 6 } }
                'c' { while ($p -lt $nums.Count) { $c1x = $cx + $nums[$p]; $c1y = $cy + $nums[$p + 1]; $c2x = $cx + $nums[$p + 2]; $c2y = $cy + $nums[$p + 3]; $ex = $cx + $nums[$p + 4]; $ey = $cy + $nums[$p + 5]; $gp.AddBezier(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $c1x * $s), ($oy + $c1y * $s), ($ox + $c2x * $s), ($oy + $c2y * $s), ($ox + $ex * $s), ($oy + $ey * $s)); $cx = $ex; $cy = $ey; $p += 6 } }
                'H' { while ($p -lt $nums.Count) { $nx = $nums[$p]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $nx * $s), ($oy + $cy * $s)); $cx = $nx; $p += 1 } }
                'h' { while ($p -lt $nums.Count) { $nx = $cx + $nums[$p]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $nx * $s), ($oy + $cy * $s)); $cx = $nx; $p += 1 } }
                'V' { while ($p -lt $nums.Count) { $ny = $nums[$p]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $cx * $s), ($oy + $ny * $s)); $cy = $ny; $p += 1 } }
                'v' { while ($p -lt $nums.Count) { $ny = $cy + $nums[$p]; $gp.AddLine(($ox + $cx * $s), ($oy + $cy * $s), ($ox + $cx * $s), ($oy + $ny * $s)); $cy = $ny; $p += 1 } }
                'Z' { $gp.CloseFigure(); $cx = $sx; $cy = $sy }
                'z' { $gp.CloseFigure(); $cx = $sx; $cy = $sy }
            }
        }
    }
}

function Draw-Icon {
    param([int]$size, [bool]$round, [string]$outPath)
    $scale = $size / 108.0
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.Color]::FromArgb(255, 0x23, 0x2A, 0x56),
        [System.Drawing.Color]::FromArgb(255, 0x0D, 0x10, 0x17),
        45.0)
    if ($round) { $g.FillEllipse($grad, 0, 0, $size, $size) } else { $g.FillRectangle($grad, $rect) }

    $green = [System.Drawing.Color]::FromArgb(255, 0x00, 0xE6, 0x76)
    $pen = New-Object System.Drawing.Pen($green, (5.0 * $scale))
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $r = 31.5 * $scale; $cx = 54 * $scale; $cy = 52 * $scale
    $g.DrawArc($pen, ($cx - $r), ($cy - $r), (2 * $r), (2 * $r), 295.0, 310.0)
    $pen.Dispose()

    $brush = New-Object System.Drawing.SolidBrush($green)
    $pts = @(
        (New-Object System.Drawing.PointF((47.9 * $scale), (20.1 * $scale))),
        (New-Object System.Drawing.PointF((42.2 * $scale), (26.8 * $scale))),
        (New-Object System.Drawing.PointF((39.2 * $scale), (20.2 * $scale)))
    )
    $g.FillPolygon($brush, $pts)
    $brush.Dispose()

    $gp = New-Object System.Drawing.Drawing2D.GraphicsPath
    Add-PathData $gp $callPath (1.55 * $scale) (35.4 * $scale) (33 * $scale)
    $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $g.FillPath($white, $gp)
    $white.Dispose(); $gp.Dispose()

    $g.Dispose()
    $dir = Split-Path $outPath -Parent
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$root = Split-Path $PSScriptRoot -Parent
$resDirs = @(
    @('mipmap-mdpi', 48),
    @('mipmap-hdpi', 72),
    @('mipmap-xhdpi', 96),
    @('mipmap-xxhdpi', 144),
    @('mipmap-xxxhdpi', 192)
)
foreach ($x in $resDirs) {
    $dir = Join-Path (Join-Path $root 'app\src\main\res') $x[0]
    Draw-Icon $x[1] $false (Join-Path $dir 'ic_launcher.png')
    Draw-Icon $x[1] $true (Join-Path $dir 'ic_launcher_round.png')
}
Draw-Icon 512 $true (Join-Path (Join-Path $root 'art') 'logo.png')
Write-Host 'icons done'
