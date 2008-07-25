#!/usr/bin/python
import sys
import os
python_path = os.environ['PYTHONPATH']
try:
    if not os.path.exists(python_path): 
        os.mkdir(python_path)
        raise ImportError
    import clonedigger.clonedigger  
except ImportError:
#    os.system('python ./setup.py easy_install --install-dir . clonedigger')
    os.chdir(python_path)
    print 'Missing Clone Digger'
    print 'We will try now to install it to local directory', python_path
    print 'please wait...'
    sys.argv = [sys.argv[0], 'easy_install', '--install-dir', python_path, 'clonedigger']
    try:
	       import setup
    except:
	       import setup
    sys.exit(143)
clonedigger.clonedigger.main()
