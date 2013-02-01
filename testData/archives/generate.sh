#!/bin/bash


function gen_file() {
    echo "$1" > "$1"
}

function gen_dir() {
    mkdir -p $*
}

function gen_zip() {
    zip -r -0 $*
}

function gen_tar_gz() {
    tar -czvpf $*
}

function gen_tar() {
    tar -cvpf $*
}
function gen_archs() {
    name="$1"
    shift
    gen_zip    "$name.zip"    $*
    gen_zip -D "$name-d.zip"  $*
    gen_tar    "$name.tar"    $*
    gen_tar_gz "$name.tar.gz" $*
}

# Part1
gen_dir "a/b/c"
gen_dir "a/b/d"
gen_dir "a/e"
gen_dir "a/e/f"
gen_file "a/b/c/01.txt"
gen_file "a/b/c/02.txt"
gen_file "a/b/d/11.txt"
gen_file "a/e/21.txt"
gen_file "a/e/22.txt"
gen_file "a/e/23.txt"
gen_file "a/e/f/31.txt"
gen_archs "1" "a"

rm -r "a"

# Part2
gen_dir "a/b/c"
gen_dir "a/b/d"
gen_dir "a/e"
gen_dir "a/e/f"
gen_file "a/b/c/01.txt"
gen_file "a/b/c/02.txt"
gen_file "a/b/d/11.txt"
gen_file "a/e/21.txt"
gen_file "a/e/22.txt"
gen_file "a/e/23.txt"
gen_file "a/e/f/31.txt"
gen_dir "z"
gen_archs "z/2" "a"
gen_zip "pad.zip" "z"
gen_zip "over.zip" "pad.zip"
rm -r "a" "z"





# 
# mkdir -p a/b
# mkdir -p d/e
# mkdir -p g/h
# gen_file "g/h/i.txt"
# zip -r d/e/f.zip g
# rm -r g
# zip -r a/b/c.zip d
# rm -r d
