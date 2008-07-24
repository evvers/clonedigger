#!/usr/bin/python
import sys
import os
try:
    import clonedigger.clonedigger
except ImportError:
#    os.system('python ./setup.py easy_install --install-dir . clonedigger')
    python_path = os.environ['PYTHONPATH']
    assert(';' not in python_path)
    assert(':' not in python_path)
    sys.argv = [sys.argv[0], 'easy_install', '--install-dir',python_path, 'clonedigger']
    import setup
    sys.exit(143)
clonedigger.clonedigger.main()
