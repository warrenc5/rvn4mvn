package rvn;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
 * @author wozza
 */
public class Hasher {

    private MessageDigest md = null;
    private Logger log = Logger.getLogger(this.getClass().getName());
    Map<String, String> hashes;

    public static Hasher instance;

    static {
        instance = new Hasher();
    }

    public static Hasher getInstance() {
        return instance;
    }

    public Hasher() {
        hashes = new HashMap<>();
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            log.warning(e.getMessage());
        }

    }

    private boolean compareHashes(Path bPath, Path rPath) {

        if (bPath == null || rPath == null) {
            return false;
        }

        String bHash = this.hashes.get(bPath.toString());
        String rHash = this.hashes.get(rPath.toString());

        if (bHash == null || rHash == null) {
            log.info("no hash");
            return false;
        }
        return bHash.equals(rHash);
    }

    public boolean hashChange(Path path) {
        try {
            if (hashes.containsKey(path.toString()) && toSHA1(path).equals(hashes.get(path.toString()))) {
                log.info("no hash change detected " + path);
                return false;
            }
        } catch (Exception x) {
            log.warning("hash " + path.toString() + " " + x.getMessage());
        }
        return true;
    }

    public boolean update(Path path) {

        boolean updated = false;
        String newHash = null;

        if (path != null) {
            try {
                String oldHash = hashes.put(path.toString(), newHash = this.toSHA1(path));
                updated = oldHash != null && oldHash != newHash;

                writeHashes();
            } catch (java.nio.charset.MalformedInputException ex) {
                log.warning("hash " + path.toString() + " " + ex.getMessage());
            } catch (IOException ex) {
                log.warning("hash " + path.toString() + " " + ex.getMessage());
            }
        }
        return updated;
    }

    public void writeHashes() throws IOException {

        try {
            FileOutputStream fos = new FileOutputStream(Config.hashConfig.toFile());
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.hashes);
            fos.flush();
        } catch (IOException x) {
            log.info("write hashes " + x.getMessage());
        }
    }

    public void readHashes() throws IOException {

        if (Files.exists(Config.hashConfig)) {
            try {
                FileInputStream fis = new FileInputStream(Config.hashConfig.toFile());
                ObjectInputStream ois = new ObjectInputStream(fis);
                this.hashes = (Map<String, String>) ois.readObject();
            } catch (IOException x) {
                log.warning("read hashes " + x.getMessage());
            } catch (ClassNotFoundException x) {
                log.warning("cnf " + x.getMessage());
            }
        } else {
            log.info("no hashes found " + Config.hashConfig.toAbsolutePath());
        }
    }

    public synchronized String toSHA1(Path value) throws IOException {
        md.update(Long.toString(Files.size(value)).getBytes());
        Files.lines(value).forEach(s -> md.update(s.getBytes()));
        return new String(md.digest());
    }

}
