convert -size 256x256 canvas:none -fill black -draw 'circle 127.5,127.5 0,127.5' circle.png
convert -size 512x512 'radial-gradient:black-none' \
	-sigmoidal-contrast 6,50% \
	airbrush.png
