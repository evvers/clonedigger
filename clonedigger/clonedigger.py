#!/usr/bin/python

#    Copyright 2008 Peter Bulychev
#
#    This file is part of Clone Digger.
#
#    Clone Digger is free software: you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    Clone Digger is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#   You should have received a copy of the GNU General Public License
#   along with Clone Digger.  If not, see <http://www.gnu.org/licenses/>.

import re
import os
import getopt
import sys
import traceback
import pdb

import clone_detection_algorithm
import arguments 
import html_report

output_file_name = 'output.html'

help_string = """To run Clone Digger type:
python clonedigger.py [OPTION]... [SOURCE FILE OR DIRECTORY]...

The typical usage is:
python clonedigger.py source_file_1 source_file_2 ...
  or
python clonedigger.py --recursive path_to_source_tree
Don't forget to remove automatically generated sources, tests and third party libraries from the source tree.

Options:
The semantics of threshold options is discussed in the paper "Duplicate code detection using anti-unification", which can be downloaded from the site http://clonedigger.sourceforge.net . All arguments are optional. Supported options are: 
--language=LANGUAGE , the programming language of source files searched for clones. Supported values are 'python'(by default) and 'java'. Warning: Clone Digger could not recognize the language of parsed sources automatically.
--recursive , traverse directories recursively. 
--output=FILE NAME , the name of the output file ('output.html' by default). 
--size-threshold=THRESHOLD, the minimum clone size. The clone size for its turn is equal to the count of lines of code in its the largest fragment.
--distance-threshold=THRESHOLD, the maximum amount of differences between pair of sequences in clone pair (5 by default). Larger value leads to larger amount of false positives.
--hashing-depth=DEPTH, default value if 1, read the paper for semantics. Compuation can be speed up by increasing increasing this value (but some clones can be list).
--clustering-threshold=THRESHOLD, read the paper for semantics.
--clusterize-using-hash, mark each statement with its D-cup value instead of the most similar pattern. This option together with --hashing-depth=0 make it possible to catch all considered clones (but it is slow and applicable only to small programs)."""

from ast_suppliers import *
if __name__ == '__main__':
    optlist, source_file_names = getopt.getopt(sys.argv[1:], '', ['language=', 'output=', 'clustering-threshold=', 'distance-threshold=', 'hashing-depth=', 'size-threshold=', 'clusterize-using-hash', 'recursive', 'report-statement-marks', 'help', 'dont-print-time', 'force', 'force-diff'])
    source_files = [] 
    #TODO remove in release
    supplier = abstract_syntax_tree_suppliers['python']
    report = html_report.HTMLReport()    
    recursive = False 
    if ('--help', '') in optlist:
		print help_string
		sys.exit(0)

    for (parameter, value) in optlist:
	if parameter == '--language':
	    assert(abstract_syntax_tree_suppliers.has_key(value))
	    supplier = abstract_syntax_tree_suppliers[value]
	    if value == 'java':
		arguments.use_diff = True
    
    arguments.size_threshold = supplier.size_threshold
    arguments.distance_threshold = supplier.distance_threshold

    for (parameter, value) in optlist:
	if parameter == '--output':
	    output_file_name = value
	elif parameter == '--clustering-threshold':
	    arguments.clustering_threshold = int(value)
	elif parameter == '--distance-threshold':
	    arguments.distance_threshold = int(value)
	elif parameter == '--size-threshold':
	    arguments.size_threshold = int(value)
	elif parameter == '--hashing-depth':
	    arguments.hashing_depth = int(value)
	elif parameter == '--clusterize-using-hash':
	    arguments.clusterize_using_hash = True
	elif parameter == '--recursive':
	    recursive = True
	elif parameter == '--report-statement-marks':
	    antiunification.report_unifiers = True
	elif parameter == '--dont-print-time':
	    arguments.print_time = False 
	elif parameter == '--force':
	    arguments.force = True
	elif parameter == '--force-diff':
	    arguments.use_diff = True
    report.startTimer('Construction of AST')
    for file_name in source_file_names:
	def parse_file(file_name):
	    try:
		print 'Parsing ', file_name, '...',
		sys.stdout.flush()
		source_file = supplier(file_name)
		source_file.getTree().propagateCoveredLineNumbers()
		source_files.append(source_file)
		report.addFileName(file_name)		
		print 'done'
	    except:
		s = 'Error: can\'t parse "%s" \n: '%(file_name,) + traceback.format_exc()
		report.addErrorInformation(s)
		print s
	if os.path.isdir(file_name):
	    def parse_dir(root, files):
		for rel_file_name in files:
		    if re.search('\.%s$'%(supplier.extension,), rel_file_name):
			full_file_name = os.path.join(root, rel_file_name)
			parse_file(full_file_name)
	    if recursive:
		for root, dir, files in os.walk(file_name):
		    parse_dir(root, files)
	    else:
		parse_dir(file_name, os.listdir(file_name))
	else:
	    parse_file(file_name)
	
    report.stopTimer()
    duplicates = clone_detection_algorithm.findDuplicateCode(source_files, report)
    for duplicate in duplicates:
	report.addClone(duplicate)
    report.sortByCloneSize()
    report.writeReport(output_file_name)
