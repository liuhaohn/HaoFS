package com.hao.server.util;

import com.hao.printer.Printer;
import com.hao.server.enumeration.AccountAuth;
import com.hao.server.mapper.FolderMapper;
import com.hao.server.mapper.NodeMapper;
import com.hao.server.model.Folder;
import com.hao.server.model.Node;
import com.hao.server.pojo.ExtendStores;
import org.apache.commons.io.FileSystemUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.Part;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;

/**
 * <h2>文件块整合操作工具</h2>
 * <p>
 * 该工具内包含了对文件系统中文件块的所有操作，使用IOC容器进行管理。
 * </p>
 *
 * @version 1.1
 */
@Component
public class FileBlockUtil {
    @Autowired
    private NodeMapper nodeMapper;// 节点映射，用于遍历
    @Autowired
    private FolderMapper folderMapper;// 文件夹映射，同样用于遍历
    @Autowired
    private LogUtil lu;// 日志工具
    @Autowired
    private FolderUtil fu;// 文件夹操作工具

    /**
     * <h2>清理临时文件夹</h2>
     * <p>
     * 该方法用于清理临时文件夹（如果临时文件夹不存在，则创建它），避免运行时产生的临时文件堆积。该方法应在服务器启动时和关闭过程中调用。
     * </p>
     */
    public void initTempDir() {
        final String tfPath = ConfigureReader.instance().getTemporaryfilePath();
        final File f = new File(tfPath);
        if (f.isDirectory()) {
            try {
                Iterator<Path> listFiles = Files.newDirectoryStream(f.toPath()).iterator();
                while (listFiles.hasNext()) {
                    listFiles.next().toFile().delete();
                }
            } catch (IOException e) {
                lu.writeException(e);
                Printer.instance.print("错误：临时文件清理失败，请手动清理" + f.getAbsolutePath() + "文件夹内的临时文件。");
            }
        } else {
            if (!f.mkdir()) {
                Printer.instance.print("错误：无法创建临时文件夹" + f.getAbsolutePath() + "，请检查主文件系统存储路径是否可用。");
            }
        }
    }

    /**
     * <h2>将新上传的文件存入文件系统</h2>
     * <p>
     * 将一个MultipartFile类型的文件对象存入节点，并返回保存的路径名称。其中，路径名称使用“file_{UUID}.block”
     * （存放于主文件系统中）或“{存储区编号}_{UUID}.block”（存放在指定编号的扩展存储区中）的形式。
     * </p>
     *
     * @param f MultipartFile 上传文件对象
     * @return java.io.File 生成的文件块，如果保存失败则返回null
     */
    public File saveMultipartBlock(final MultipartFile f, String prefix) {
        // 不存在扩展存储区或者最大的扩展存储区无法存放目标文件，则尝试将其存放至主文件系统路径下
        try {
            final File file = createNewBlock(prefix + "_", new File(ConfigureReader.instance().getFileBlockPath()));
            if (file != null) {
                f.transferTo(file);// 执行存放，并肩文件命名为“file_{UUID}.block”的形式
                return file;
            }
        } catch (Exception e) {
            lu.writeException(e);
            Printer.instance.print("错误：文件块生成失败，无法存入新的文件数据。详细信息：" + e.getMessage());
        }
        return null;
    }

    public File savePartBlock(final Part f, String prefix) {
        // 不存在扩展存储区或者最大的扩展存储区无法存放目标文件，则尝试将其存放至主文件系统路径下
        try {
            final File file = createNewBlock(prefix + "_", new File(ConfigureReader.instance().getFileBlockPath()));
            if (file != null) {
                IOUtils.copy(f.getInputStream(), new FileOutputStream(file));
                return file;
            }
        } catch (Exception e) {
            lu.writeException(e);
            Printer.instance.print("错误：文件块生成失败，无法存入新的文件数据。详细信息：" + e.getMessage());
        }
        return null;
    }

    /**
     * <h2>将新上传的文件存入文件系统</h2>
     * <p>
     * 将一个MultipartFile类型的文件对象存入节点，并返回保存的路径名称。其中，路径名称使用“file_{UUID}.block”
     * （存放于主文件系统中）或“{存储区编号}_{UUID}.block”（存放在指定编号的扩展存储区中）的形式。
     * </p>
     *
     * @param f MultipartFile 上传文件对象
     * @return java.io.File 生成的文件块，如果保存失败则返回null
     */
    public File saveBlock(final InputStream f, String fileName) {
        // 不存在扩展存储区或者最大的扩展存储区无法存放目标文件，则尝试将其存放至主文件系统路径下
        FileOutputStream fileOutputStream = null;
        try {
            final File file = new File(ConfigureReader.instance().getFileBlockPath() + fileName);
            fileOutputStream = new FileOutputStream(file);
            IOUtils.copy(f, fileOutputStream);
            return file;
        } catch (Exception e) {
            lu.writeException(e);
            Printer.instance.print("错误：文件块生成失败，无法存入新的文件数据。详细信息：" + e.getMessage());
        } finally {
            IOUtils.closeQuietly(f);
            IOUtils.closeQuietly(fileOutputStream);
        }
        return null;
    }

    // 生成创建一个在指定路径下名称（编号）绝对不重复的新文件块
    private File createNewBlock(String prefix, File parent) throws IOException {
        int appendIndex = 0;
        int retryNum = 0;
        String newName = prefix + UUID.randomUUID().toString().replace("-", "");
        File newBlock = new File(parent, newName + ".block");
        while (!newBlock.createNewFile()) {
            if (appendIndex >= 0 && appendIndex < Integer.MAX_VALUE) {
                newBlock = new File(parent, newName + "_" + appendIndex + ".block");
                appendIndex++;
            } else {
                if (retryNum >= 5) {
                    return null;
                } else {
                    newName = prefix + UUID.randomUUID().toString().replace("-", "");
                    newBlock = new File(parent, newName + ".block");
                    retryNum++;
                }
            }
        }
        return newBlock;
    }


    /**
     * <h2>删除文件系统中的一个文件块</h2>
     * <p>
     * 根据传入的文件节点对象，删除其在文件系统中保存的对应文件块。仅当传入文件节点所对应的文件块不再有其他节点引用时
     * 才会真的进行删除操作，否则直接返回true。
     * </p>
     *
     * @param f Node 要删除的文件节点对象
     * @return boolean 删除结果，true为成功
     */
    public boolean deleteFromFileBlocks(Node f) {
        // 检查是否还有其他节点引用相同的文件块
        Map<String, String> map = new HashMap<>();
        map.put("path", f.getFilePath());
        map.put("fileId", f.getFileId());
        List<Node> nodes = nodeMapper.queryByPathExcludeById(map);
        if (nodes == null || nodes.isEmpty()) {
            // 如果已经无任何节点再引用此文件块，则删除它
            File file = getFileFromBlocks(f);// 获取对应的文件块对象
            if (file != null) {
                return file.delete();// 执行删除操作
            }
            return false;
        } else {
            // 如果还有，那么直接返回true即可，认为此节点的文件块已经删除了（其他的引用是属于其他节点的）
            return true;
        }
    }

    /**
     * <h2>得到文件系统中的一个文件块</h2>
     * <p>
     * 根据传入的文件节点对象，得到其在文件系统中保存的对应文件块。
     * </p>
     *
     * @param f Node 要获得的文件节点对象
     * @return java.io.File 对应的文件块抽象路径，获取失败则返回null
     */
    public File getFileFromBlocks(Node f) {
        // 检查该节点对应的文件块存放于哪个位置（主文件系统/扩展存储区）
        try {
            File file = new File(ConfigureReader.instance().getFileBlockPath(), f.getFilePath());
            if (file.isFile()) {
                return file;
            }
        } catch (Exception e) {
            lu.writeException(e);
            Printer.instance.print("错误：文件数据读取失败。详细信息：" + e.getMessage());
        }
        return null;
    }

    /**
     * <h2>校对文件块与文件节点</h2>
     * <p>
     * 将文件系统中不可用的文件块移除，以便保持文件系统的整洁。该操作应在服务器启动或出现问题时执行。
     * </p>
     */
    public void checkFileBlocks() {
        Thread checkThread = new Thread(() -> {
            // 检查是否存在未正确对应文件块的文件节点信息，若有则删除，从而确保文件节点信息不出现遗留问题
            checkNodes("root");
            // 检查是否存在未正确对应文件节点的文件块，若有则删除，从而确保文件块不出现遗留问题
            List<File> paths = new ArrayList<>();
            paths.add(new File(ConfigureReader.instance().getFileBlockPath()));
            for (ExtendStores es : ConfigureReader.instance().getExtendStores()) {
                paths.add(es.getPath());
            }
            for (File path : paths) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(path.toPath())) {
                    Iterator<Path> blocks = ds.iterator();
                    while (blocks.hasNext()) {
                        File testBlock = blocks.next().toFile();
                        if (testBlock.isFile()) {
                            List<Node> nodes = nodeMapper.queryByPath(testBlock.getName());
                            if (nodes == null || nodes.isEmpty()) {
                                testBlock.delete();
                            }
                        }
                    }
                } catch (IOException e) {
                    Printer.instance.print("警告：文件节点效验时发生意外错误，可能未能正确完成文件节点效验。错误信息：" + e.getMessage());
                    lu.writeException(e);
                }
            }
        });
        checkThread.start();
    }

    // 校对文件节点
    private void checkNodes(String fid) {
        List<Node> nodes = nodeMapper.queryByParentFolderId(fid);
        for (Node node : nodes) {
            File block = getFileFromBlocks(node);
            if (block == null) {
                nodeMapper.deleteById(node.getFileId());
            }
        }
    }

    /**
     * <h2>将指定节点及文件夹打包为ZIP压缩文件。</h2>
     * <p>
     * 该功能用于创建ZIP压缩文件，线程阻塞。如果压缩目标中存在同名情况，则使用“{文件名} (n).{后缀}”或“{文件夹名} n”的形式重命名。
     * </p>
     *
     * @param idList  java.util.List<String> 要压缩的文件节点目标ID列表
     * @param fidList java.util.List<String> 要压缩的文件夹目标ID列表，迭代压缩
     * @param account java.lang.String 用户ID，用于判断压缩文件夹是否有效
     * @return java.lang.String
     * 压缩后产生的文件名称，命名规则为“tf_{UUID}.zip”，存放于文件系统中的temporaryfiles目录下
     */
    public String createZip(final List<String> idList, final List<String> fidList, String account) {
        final String zipname = "tf_" + UUID.randomUUID().toString() + ".zip";
        final String tempPath = ConfigureReader.instance().getTemporaryfilePath();
        final File f = new File(tempPath, zipname);
        try {
            final List<ZipEntrySource> zs = new ArrayList<>();
            // 避免压缩时出现同名文件导致打不开：
            final List<Folder> folders = new ArrayList<>();
            for (String fid : fidList) {
                Folder fo = folderMapper.queryById(fid);
                if (ConfigureReader.instance().accessFolder(fo, account) && ConfigureReader.instance()
                        .authorized(account, AccountAuth.DOWNLOAD_FILES, fu.getAllFoldersId(fo.getFolderParent()))) {
                    if (fo != null) {
                        folders.add(fo);
                    }
                }
            }
            final List<Node> nodes = new ArrayList<>();
            for (String id : idList) {
                Node n = nodeMapper.queryById(id);
                if (ConfigureReader.instance().accessFolder(folderMapper.queryById(n.getFileParentFolder()), account)
                        && ConfigureReader.instance().authorized(account, AccountAuth.DOWNLOAD_FILES,
                        fu.getAllFoldersId(n.getFileParentFolder()))) {
                    if (n != null) {
                        nodes.add(n);
                    }
                }
            }
            for (Folder fo : folders) {
                int i = 1;
                String flname = fo.getFolderName();
                while (true) {
                    if (folders.parallelStream().filter((e) -> e.getFolderName().equals(fo.getFolderName()))
                            .count() > 1) {
                        fo.setFolderName(flname + " " + i);
                        i++;
                    } else {
                        break;
                    }
                }
                addFoldersToZipEntrySourceArray(fo, zs, account, "");
            }
            for (Node node : nodes) {
                if (ConfigureReader.instance().accessFolder(folderMapper.queryById(node.getFileParentFolder()), account)) {
                    int i = 1;
                    String fname = node.getFileName();
                    while (true) {
                        if (nodes.parallelStream().filter((e) -> e.getFileName().equals(node.getFileName())).count() > 1
                                || folders.parallelStream().filter((e) -> e.getFolderName().equals(node.getFileName()))
                                .count() > 0) {
                            if (fname.indexOf(".") >= 0) {
                                node.setFileName(fname.substring(0, fname.lastIndexOf(".")) + " (" + i + ")"
                                        + fname.substring(fname.lastIndexOf(".")));
                            } else {
                                node.setFileName(fname + " (" + i + ")");
                            }
                            i++;
                        } else {
                            break;
                        }
                    }
                    zs.add((ZipEntrySource) new FileSource(node.getFileName(), getFileFromBlocks(node)));
                }
            }
            ZipUtil.pack(zs.toArray(new ZipEntrySource[0]), f);
            return zipname;
        } catch (Exception e) {
            lu.writeException(e);
            Printer.instance.print(e.getMessage());
            return null;
        }
    }

    // 迭代生成ZIP文件夹单元，将一个文件夹内的文件和文件夹也进行打包
    private void addFoldersToZipEntrySourceArray(Folder f, List<ZipEntrySource> zs, String account, String parentPath) {
        if (f != null && ConfigureReader.instance().accessFolder(f, account)) {
            String folderName = f.getFolderName();
            String thisPath = parentPath + folderName + "/";
            zs.add(new ZipEntrySource() {

                @Override
                public String getPath() {
                    return thisPath;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return null;
                }

                @Override
                public ZipEntry getEntry() {
                    return new ZipEntry(thisPath);
                }
            });
            List<Folder> folders = folderMapper.queryByParentId(f.getFolderId());
            for (Folder fo : folders) {
                int i = 1;
                String flname = fo.getFolderName();
                while (true) {
                    if (folders.parallelStream().filter((e) -> e.getFolderName().equals(fo.getFolderName()))
                            .count() > 1) {
                        fo.setFolderName(flname + " " + i);
                        i++;
                    } else {
                        break;
                    }
                }
                addFoldersToZipEntrySourceArray(fo, zs, account, thisPath);
            }
            List<Node> nodes = nodeMapper.queryByParentFolderId(f.getFolderId());
            for (Node node : nodes) {
                int i = 1;
                String fname = node.getFileName();
                while (true) {
                    if (nodes.parallelStream().filter((e) -> e.getFileName().equals(node.getFileName())).count() > 1
                            || folders.parallelStream().filter((e) -> e.getFolderName().equals(node.getFileName()))
                            .count() > 0) {
                        if (fname.indexOf(".") >= 0) {
                            node.setFileName(fname.substring(0, fname.lastIndexOf(".")) + " (" + i + ")"
                                    + fname.substring(fname.lastIndexOf(".")));
                        } else {
                            node.setFileName(fname + " (" + i + ")");
                        }
                        i++;
                    } else {
                        break;
                    }
                }
                zs.add(new FileSource(thisPath + node.getFileName(), getFileFromBlocks(node)));
            }
        }
    }

    /**
     * <h2>生成指定文件块资源对应的ETag标识</h2>
     * <p>
     * 该方法用于生产指定文件块的ETag标识，从而方便前端控制缓存。生成规则为：{文件最后修改时间计数}_{文件路径对应的Hash码}。
     * </p>
     *
     * @param block java.io.File 需要生成的文件块对象，应为文件，但也支持文件夹，或者是null
     * @return java.lang.String 生成的ETag值。当传入的block是null或其不存在时，返回空字符串
     */
    public String getETag(File block) {
        if (block != null && block.exists()) {
            StringBuffer sb = new StringBuffer();
            sb.append("\"");
            sb.append(block.lastModified());
            sb.append("_");
            sb.append(block.hashCode());
            sb.append("\"");
            return sb.toString().trim();
        }
        return "\"0\"";
    }

    /**
     * <h2>插入一个新的文件节点至文件系统数据库中</h2>
     * <p>
     * 该方法将尝试生成一个新的文件节点并存入文件系统数据库，并确保该文件节点再插入后不会与已有节点产生冲突。
     * </p>
     *
     * @param fileName         java.lang.String 文件名称
     * @param account          java.lang.String 创建者账户，若传入null则按匿名创建者处理
     * @param filePath         java.lang.String 文件节点对应的文件块索引
     * @param fileSize         java.lang.String 文件体积
     * @param fileParentFolder java.lang.String 文件的父文件夹ID
     * @return Node 操作成功则返回节点对象，否则返回null
     */
    public Node insertNewNode(String fileId, String fileName, String account, String filePath, String fileSize,
                              String fileParentFolder) {
        if (fileId == null) return null;
        final Node f2 = new Node();
        f2.setFileId(fileId);
        if (account != null) {
            f2.setFileCreator(account);
        }
        f2.setFileCreationDate(ServerTimeUtil.accurateToDay());
        f2.setFileName(fileName);
        f2.setFileParentFolder(fileParentFolder);
        f2.setFilePath(filePath);
        f2.setFileSize(fileSize);
        if (this.nodeMapper.queryById(fileId) == null) {
            if (this.nodeMapper.insert(f2) > 0) {
                return f2;
            }
        } else {
            if (this.nodeMapper.update(f2) > 0) {
                return f2;
            }
        }
        return null;
    }

    /**
     * <h2>检查指定的文件节点是否存在同名问题</h2>
     * <p>
     * 该方法用于检查传入节点是否存在冲突问题，一般在新节点插入后执行，若存在冲突会立即删除此节点，最后会返回检查结果。
     * </p>
     *
     * @param n Node 待检查的节点
     * @return boolean 通过检查则返回true，否则返回false并删除此节点
     */
    public boolean isValidNode(Node n) {
        Node[] repeats = nodeMapper.queryByParentFolderId(n.getFileParentFolder()).parallelStream()
                .filter((e) -> e.getFileName().equals(n.getFileName())).toArray(Node[]::new);
        if (repeats.length > 1) {
            // 如果插入后存在：
            // 1，该节点没有有效的父级文件夹（死节点）；
            // 2，与同级的其他节点重名，
            // 那么它就是一个无效的节点，应将插入操作撤销
            // 所谓撤销，也就是将该节点的数据立即删除（如果有）
            nodeMapper.deleteById(n.getFileId());
            return false;// 返回“无效”的判定结果
        } else {
            return true;// 否则，该节点有效，返回结果
        }
    }

    /**
     * <h2>获取一个节点当前的逻辑路径</h2>
     * <p>
     * 该方法用于获取指定节点当前的完整逻辑路径，型如“/ROOT/doc/test.txt”。
     * </p>
     *
     * @param n Node 要获取路径的节点
     * @return java.lang.String 指定节点的逻辑路径，包含其完整的上级文件夹路径和自身的文件名，各级之间以“/”分割。
     */
    public String getNodePath(Node n) {
        Folder folder = folderMapper.queryById(n.getFileParentFolder());
        List<Folder> l = fu.getParentList(folder.getFolderId());
        StringBuffer pl = new StringBuffer();
        for (Folder i : l) {
            pl.append(i.getFolderName() + "/");
        }
        pl.append(folder.getFolderName());
        pl.append("/");
        pl.append(n.getFileName());
        return pl.toString();
    }

}
