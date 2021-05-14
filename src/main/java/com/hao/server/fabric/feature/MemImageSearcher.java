package com.hao.server.fabric.feature;

import net.semanticmetadata.lire.builders.DocumentBuilder;
import net.semanticmetadata.lire.builders.GlobalDocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.features.GlobalFeature;
import net.semanticmetadata.lire.imageanalysis.features.LireFeature;
import net.semanticmetadata.lire.indexers.parallel.ExtractorItem;
import net.semanticmetadata.lire.searchers.SimpleResult;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

@Component
public class MemImageSearcher {
    protected Logger logger = Logger.getLogger(getClass().getName());
    protected String fieldName;
    protected LireFeature cachedInstance = null;
    protected ExtractorItem extractorItem;
    protected HashMap<Integer, String> fileNames = new HashMap<>();
    protected TreeSet<SimpleResult> docs = new TreeSet<>();
    protected LinkedHashMap<Integer, byte[]> featureCache = new LinkedHashMap<>();
    protected HashMap<String, Integer> nameIndex = new HashMap<>();

    protected int maxHits = 50;
    protected double maxDistance;

    protected LinkedBlockingQueue<Map.Entry<Integer, byte[]>> queue = new LinkedBlockingQueue<>(100);
    protected int numThreads = DocumentBuilder.NUM_OF_THREADS;

    public MemImageSearcher() {
        this.extractorItem = new ExtractorItem(SaCoCo.class);
        this.fieldName = extractorItem.getFieldName();
        try {
            this.cachedInstance = (GlobalFeature) extractorItem.getExtractorInstance().getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    public void addDocument(Document doc) {
        int size = featureCache.size();
        featureCache.put(size, doc.getBinaryValue("SaCoCo").bytes);
        String fileName = doc.getField("FileName").stringValue();
        fileNames.put(size, fileName);
        nameIndex.put(fileName, size);
    }

    public void removeDocument(String fileName) {
        Integer key = nameIndex.get(fileName);
        featureCache.remove(key);
        fileNames.remove(key);
    }

    /**
     * @param lireFeature
     * @return the maximum distance found for normalizing.
     * @throws IOException
     */
    private double findSimilar(LireFeature lireFeature) throws IOException {
        maxDistance = -1d;

        docs.clear();   // 结果集
        LinkedList<Consumer> tasks = new LinkedList<>();
        LinkedList<Thread> threads = new LinkedList<>();
        Consumer consumer;
        Thread thread;
        Thread p = new Thread(new Producer());
        p.start();
        for (int i = 0; i < numThreads; i++) {
            consumer = new Consumer(lireFeature);
            thread = new Thread(consumer);
            thread.start();
            tasks.add(consumer);
            threads.add(thread);
        }
        for (Thread next : threads) {
            try {
                next.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        TreeSet<SimpleResult> tmpDocs;
        boolean flag;
        SimpleResult simpleResult;
        for (Consumer task : tasks) {
            tmpDocs = task.getResult();
            flag = true;
            while (flag && (tmpDocs.size() > 0)) {
                simpleResult = tmpDocs.pollFirst();
                if (this.docs.size() < maxHits) {
                    this.docs.add(simpleResult);
                    if (simpleResult.getDistance() > maxDistance) maxDistance = simpleResult.getDistance();
                } else if (simpleResult.getDistance() < maxDistance) {
//                        this.docs.remove(this.docs.last());
                    this.docs.pollLast();
                    this.docs.add(simpleResult);
                    maxDistance = this.docs.last().getDistance();
                } else flag = false;
            }
        }
        return maxDistance;
    }

    private class Producer implements Runnable {

        private Producer() {
            queue.clear();
        }

        public void run() {
            for (Map.Entry<Integer, byte[]> documentEntry : featureCache.entrySet()) {
                try {
                    queue.put(documentEntry);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            LinkedHashMap<Integer, byte[]> tmpMap = new LinkedHashMap<>(numThreads * 3);
            for (int i = 1; i < numThreads * 3; i++) {
                tmpMap.put(-i, null);
            }
            for (Map.Entry<Integer, byte[]> documentEntry : tmpMap.entrySet()) {
                try {
                    queue.put(documentEntry);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class Consumer implements Runnable {
        private boolean locallyEnded = false;
        private TreeSet<SimpleResult> localDocs = new TreeSet<SimpleResult>();
        private LireFeature localCachedInstance;
        private LireFeature localLireFeature;

        private Consumer(LireFeature lireFeature) {
            try {
                this.localCachedInstance = cachedInstance.getClass().newInstance();
                this.localLireFeature = lireFeature.getClass().newInstance();
                this.localLireFeature.setByteArrayRepresentation(lireFeature.getByteArrayRepresentation());
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            Map.Entry<Integer, byte[]> tmp;
            double tmpDistance;
            double localMaxDistance = -1d;
            while (!locallyEnded) {
                try {
                    tmp = queue.take();
                    if (tmp.getKey() < 0) locallyEnded = true;
                    if (!locallyEnded) {    // && tmp != -1
                        localCachedInstance.setByteArrayRepresentation(tmp.getValue());
                        tmpDistance = localLireFeature.getDistance(localCachedInstance);
                        assert (tmpDistance >= 0);
                        // if the array is not full yet:
                        if (localDocs.size() < maxHits) {
                            localDocs.add(new SimpleResult(tmpDistance, tmp.getKey()));
                            if (tmpDistance > localMaxDistance) localMaxDistance = tmpDistance;
                        } else if (tmpDistance < localMaxDistance) {
                            // if it is nearer to the sample than at least on of the current set:
                            // remove the last one ...
//                            localDocs.remove(localDocs.last());
                            localDocs.pollLast();
                            // add the new one ...
                            localDocs.add(new SimpleResult(tmpDistance, tmp.getKey()));
                            // and set our new distance border ...
                            localMaxDistance = localDocs.last().getDistance();
                        }
                    }
                } catch (InterruptedException e) {
                    e.getMessage();
                }
            }
        }

        public TreeSet<SimpleResult> getResult() {
            return localDocs;
        }
    }

    public List<String> search(BufferedImage image, int maxHits) throws IOException {
        this.maxHits = maxHits;
        logger.finer("Starting extraction.");

        GlobalDocumentBuilder globalDocumentBuilder = new GlobalDocumentBuilder();
        GlobalFeature globalFeature = globalDocumentBuilder.extractGlobalFeature(image, (GlobalFeature) extractorItem.getExtractorInstance());  // image的global特征

        findSimilar(globalFeature);    // 找到n个特征最近特征保存在全局变量docs中，返回最大距离

        List<String> res = new ArrayList<>();
        for (int i = 0; i < maxHits; i++) {
            SimpleResult simpleResult = this.docs.pollFirst();
            if (simpleResult == null) break;
            String distance = String.format("%.2f", simpleResult.getDistance());
            String name = this.fileNames.get(simpleResult.getIndexNumber());
            res.add(name + "," + distance);
        }

        return res;
    }


    public String toString() {
        return "GenericSearcher using " + extractorItem.getExtractorClass().getName();
    }

}
