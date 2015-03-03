CIDRE
=====

About
-----
CIDRE is a retrospective illumination correction method for optical microscopy. It is designed to correct collections of images by building a model of the illumination distortion directly from the image data. Larger image collections provide more robust corrections. Details of the method are described in [].

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

<a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc/4.0/88x31.png" /></a><br /><span xmlns:dct="http://purl.org/dc/terms/" property="dct:title">CIDRE</span> by <a xmlns:cc="http://creativecommons.org/ns#" href="www.kev-smith.com" property="cc:attributionName" rel="cc:attributionURL">K. Smith</a> is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/">Creative Commons Attribution-NonCommercial 4.0 International License</a>.<br />Based on a work at <a xmlns:dct="http://purl.org/dc/terms/" href="https://github.com/smithk/cidre" rel="dct:source">https://github.com/smithk/cidre</a>.
