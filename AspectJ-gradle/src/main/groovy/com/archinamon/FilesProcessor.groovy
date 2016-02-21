package com.archinamon

import groovy.io.FileType

class FilesProcessor {

    def static collectAj(String ajSourceRoot) {
        def Set<File> list = [];
        def dir = new File(ajSourceRoot);
        dir.eachFileRecurse (FileType.FILES) { list << it; }

        return list;
    }

    def static collectBinary(String buildDir) {
        def Set<File> list = [];
        def dir = new File(buildDir);
        dir.eachFileRecurse (FileType.FILES) { list << it; }

        return list;
    }

    def static outterJoin(Set<File> left, Set<File> right) {
        def Set<String> rlist = [];
        right.each {
            rlist << it.name.substring(0, it.name.lastIndexOf("."));
        }

        def Set<File> list = [];
        left.each { final lfile ->
            if (lfile != null) {
                def boolean add = true;
                def name = lfile.name.substring(0, lfile.name.lastIndexOf("."));

                for (String r : rlist) {
                    if (r.equals(name)) {
                        add = false;
                        break;
                    };
                }

                if (add) {
                    if (!list.contains(lfile.parentFile)) list << lfile.parentFile;
                };
            };
        }

        return list;
    }
}