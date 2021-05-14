package com.hao.server.fabric.feature;

import net.semanticmetadata.lire.searchers.SimpleResult;

import java.util.HashMap;
import java.util.TreeSet;

class FileImageSearchHits {
    private TreeSet<SimpleResult> docs;
    private double maxDistance;

    private HashMap<Integer, String> fileName;

    public FileImageSearchHits(TreeSet<SimpleResult> docs, double maxDistance, HashMap<Integer, String> fileName) {
        this.docs = docs;
        this.maxDistance = maxDistance;
        this.fileName = fileName;
    }

    public TreeSet<SimpleResult> getDocs() {
        return docs;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public HashMap<Integer, String> getFileName() {
        return fileName;
    }
}
