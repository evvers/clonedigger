#!/usr/bin/python
import sys
import os
try:
    import clonedigger.clonedigger
except ImportError:
#    os.system('python ./setup.py easy_install --install-dir . clonedigger')
    sys.argv = [sys.argv[0], 'easy_install', '--install-dir','.', 'clonedigger']
    import setup
    sys.exit(143)
clonedigger.clonedigger.main()
