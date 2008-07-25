#!/usr/bin/python
import sys
import os
python_path = os.environ['PYTHONPATH']
try:
    import clonedigger.clonedigger
    if not os.path.exists(python_path): raise ImportError  
except ImportError:
#    os.system('python ./setup.py easy_install --install-dir . clonedigger')
    if not os.path.exists(python_path): os.mkdir(python_path)
    os.chdir(python_path)
    print 'Missing Clone Digger'
    print 'We will try now to install it to local directory', python_path
    print 'please wait...'
    sys.argv = [sys.argv[0], 'easy_install', '--install-dir',python_path, 'clonedigger']
    try:
	       import setup
    except:
	       import setup
    sys.exit(143)
clonedigger.clonedigger.main()
