#!/usr/bin/env python

from tempfile import mkstemp
from os import remove
from shutil import move

LISTA = [('.',2),('.',2),('"',0),('"',2)]
BUILDFILE = 'build.sbt'

def general(text, params):
    if not params:
        try:
            return str(int(text)+1)
        except ValueError:
            return '0'
    else:
        our_params = params.pop()
        partitioned = list(text.partition(our_params[0]))
        partitioned[our_params[1]] = general(partitioned[our_params[1]], params)
        return ''.join(partitioned)

def main():
    fh, abs_path = mkstemp()

    with open(BUILDFILE, 'r') as f:
        with open(abs_path, 'w') as e:
            for line in f.readlines():
                if line.startswith('version'):
                    line = general(line, LISTA)
                e.write(line)

    remove(BUILDFILE)
    move(abs_path, BUILDFILE)

if __name__ == "__main__":
    main()
