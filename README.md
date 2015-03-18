CIDRE
=====

About
-----
CIDRE is a retrospective illumination correction method for optical microscopy. It is designed to correct collections of images by building a model of the illumination distortion directly from the image data. Larger image collections provide more robust corrections. Details of the method are described in 
<ol>
<li>
K. Smith, Y. Li, F. Ficcinini, G. Csucs, A. Bevilacqua, and P. Horvath<br>
<a href="http://www.nature.com/nmeth/journal/vaop/ncurrent/full/nmeth.3323.html">CIDRE: An Illumination Correction Method for Optical Microscopy</a>,
Nature Methods, <em>Early Online Access 16 March 2015</em>, doi:10.1038/NMETH.3323
</li>
</ol>.

Contents
--------
This project contains several folders:

- ``matlab`` a standalone Matlab implementation of CIDRE with a GUI interface.

- ``compiled`` a compiled executable of the Matlab standalone that runs without a Matlab installation.

- ``imagej``  an ImageJ plugin implementation of CIDRE.


Installation
------------

To install the ImageJ plugin, simply copy `Cidre_Plugin.jar` to the plugins folder of your ImageJ or Fiji installation. For instructions on how to use the Matlab implementation, add the CIDRE/matlab folder to your path and type `help cidre`.

============

Copyright © 2015 Kevin Smith, ETH Zurich (Swiss Federal Institute of Technology Zurich), Switzerland. All rights reserved.

CIDRE is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License version 2 (or higher) 
as published by the Free Software Foundation. See LICENSE.md. This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

<a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc/4.0/88x31.png" /></a><br /><span xmlns:dct="http://purl.org/dc/terms/" property="dct:title">CIDRE uses minFunc</span> by Mark Schmidt which is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/">Creative Commons Attribution-NonCommercial 4.0 International License</a>.
