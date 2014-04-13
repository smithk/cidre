CIDRE
=====

About
-----
CIDRE is a general illumination correction method for optical microscopy. It is designed to correct collections of images by building a model of the illumination distortion directly from the image data. Larger image collections provide more robust corrections. Details of the method are described in [].

Contents
--------
This project contains several folders:

- ``matlab`` a standalone Matlab implementation of CIDRE with a GUI interface.

- ``cellprofiler`` a CellProfiler module implementation of CIDRE.

- ``compiled`` a compiled executable of the Matlab standalone that runs without a Matlab installation.

- ``imagej``  an ImageJ plugin implementation of CIDRE.


Installation
------------

To install the ImageJ plugin, simply copy `Cidre_Plugin.jar` to the plugins folder of your ImageJ or Fiji installation. For instructions on how to use the Matlab implementation, add the CIDRE/matlab folder to your path and type `help cidre`.

============

Copyright © 2014 Kevin Smith, Light Microscopy and Screening Centre (LMSC),  Swiss Federal Institute of Technology Zurich (ETHZ), Switzerland. All rights  reserved.

This program is free software; you can redistribute it and/or modify it  under the terms of the GNU General Public License version 2 (or higher)  as published by the Free Software Foundation. This program is  distributed WITHOUT ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU  General Public License for more details. 
