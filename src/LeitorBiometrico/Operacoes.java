/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package LeitorBiometrico;

/**
 *
 * @author renan.rosa
 */
import com.nitgen.SDK.BSP.NBioBSPJNI;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.log4j.Logger;

/**
 *
 * @author renan.rosa
 */
public class Operacoes {

    NBioBSPJNI bsp;
    NBioBSPJNI.IndexSearch IndexSearchEngine;
    String host;
    String user;
    String password;
    String databasePath;
    String database = "data/database";
    String databaseFTP = "";
    String[] extensoes = {".ISDB", ".FID"};
    private static final String ALGO = "AES";
    private static final byte[] KEY_VALUE
            = new byte[]{'T', 'h', 'e', 'B', 'e', 's', 't', 'S', 'e', 'c', 'r', 'e', 't', 'K', 'e', 'y'};
    final static Logger LOGGER = Logger.getLogger(Operacoes.class.getName());

    public Operacoes() {
        carregarArquivoDeConfiguracao();
        databaseFTP = "ftp://" + user + ":" + password + "@" + host + "/" + databasePath;
        bsp = new NBioBSPJNI();
        if (CheckError()) {
            return;
        }
        this.IndexSearchEngine = bsp.new IndexSearch();

        if (CheckError()) {
            return;
        }
        bsp.OpenDevice();
    }

    public boolean verificarDispositivo() {
        LOGGER.info("Verificando dispositivo:");
        return CheckError();
    }

    public String verificarDigital(String cpf) {

        carregarBancoDeDadosDoFTP();
        cpf = cpf.replace("[", "").replace("]", "");
        String[] cpfs = cpf.split(" ");

        int nMaxSearchTime = 0;
        NBioBSPJNI.FIR_HANDLE hCapturedFIR = bsp.new FIR_HANDLE();
        bsp.Capture(hCapturedFIR);
        NBioBSPJNI.INPUT_FIR inputFIR = bsp.new INPUT_FIR();
        inputFIR.SetFIRHandle(hCapturedFIR);
        NBioBSPJNI.IndexSearch.FP_INFO fpInfo = IndexSearchEngine.new FP_INFO();
        IndexSearchEngine.Identify(inputFIR, 5, fpInfo, nMaxSearchTime);
        String erro = null;

        if (CheckError()) {
            if (bsp.GetErrorCode() == NBioBSPJNI.ERROR.NBioAPIERROR_INDEXSEARCH_IDENTIFY_FAIL) {
                erro = "Atenção: Usuário não encontrado";
            } else if (bsp.GetErrorCode() == NBioBSPJNI.ERROR.NBioAPIERROR_INDEXSEARCH_IDENTIFY_STOP) {
                erro = "Atenção: Timeout";
            }
            LOGGER.info(erro);
            return erro;
        }

        LOGGER.info("Comparar: " + fpInfo.ID);
        for (String encontrar : cpfs) {
            LOGGER.info("Encontrar: " + encontrar);
            int hashCode = encontrar.hashCode();
            if (hashCode == fpInfo.ID) {
                return "true";
            }
        }

        int hashCode = cpf.hashCode();
        if (hashCode == fpInfo.ID) {
            return "true";
        } else {
            return "digital encontrada, cpf invalido";
        }
    }

    private Boolean CheckError() {
        if (bsp.IsErrorOccured()) {
            LOGGER.error("NBioBSP Error Occured [" + bsp.GetErrorCode() + "]");
            return true;
        }
        return false;
    }

    public void dispose() {
        if (IndexSearchEngine != null) {
            IndexSearchEngine.dispose();
            IndexSearchEngine = null;
        }

        if (bsp != null) {
            bsp.CloseDevice();
            bsp.dispose();
            bsp = null;
        }
    }

    public String cadastrarDigital(String cpf) {

        NBioBSPJNI.FIR_HANDLE hFIR = bsp.new FIR_HANDLE();
        bsp.Enroll(hFIR, null);

        if (CheckError()) {
            if (bsp.GetErrorCode() == NBioBSPJNI.ERROR.NBioAPIERROR_FUNCTION_FAIL) {
                bsp.Capture(hFIR);
            }
            if (CheckError()) {
                return "error";
            }
        }

        NBioBSPJNI.INPUT_FIR inputFIR = bsp.new INPUT_FIR();
        NBioBSPJNI.IndexSearch.SAMPLE_INFO sampleInfo = IndexSearchEngine.new SAMPLE_INFO();
        inputFIR.SetFIRHandle(hFIR);

        int hashCode = cpf.hashCode();
        LOGGER.info("input hash code = " + hashCode);

        IndexSearchEngine.AddFIR(inputFIR, hashCode, sampleInfo);

        if (CheckError()) {
            return "error";
        }

        hFIR.dispose();
        hFIR = null;
        carregarBancoDeDadosDoFTP();
        salvarBancoDeDadosLocalmente(cpf);
        uploadBancoDeDadosParaFTP();
        LOGGER.info("Registration success");
        return "true";
    }

    public void salvarBancoDeDadosLocalmente(String cpf) {

        LOGGER.info("SaveDB start");
        String szSavePath = database + ".ISDB";
        IndexSearchEngine.SaveDB(szSavePath);

        if (CheckError()) {
            return;
        }

        if (!cadastroDigitalExiste(cpf)) {
            BufferedWriter writer = null;
            try {
                szSavePath = database + ".FID";
                writer = new BufferedWriter(new FileWriter(szSavePath, true));
                writer.newLine();
                writer.append(cpf);
                writer.close();

            } catch (IOException ex) {
                LOGGER.error(ex);
            } finally {
                try {
                    writer.close();
                } catch (IOException ex) {
                    LOGGER.error(ex);
                }
            }
        }
        LOGGER.info("SaveDB success");
    }

    public void carregarDB() {

        LOGGER.info("Carregando banco de dados");
        String szLoadPath = database + ".ISDB";

        if (CheckError()) {
            return;
        }

        IndexSearchEngine.LoadDB(szLoadPath);

        if (CheckError()) {
            return;
        }
        java.io.BufferedReader reader = null;

        try {
            String szFileContents = null;
            String[] szLoadFIDPath = szLoadPath.split(".ISDB");
            reader = new java.io.BufferedReader(new java.io.FileReader(szLoadFIDPath[0] + ".FID"));
            do {
                szFileContents = reader.readLine();

                if (szFileContents != null) {
                    String[] arContents = szFileContents.split("\t");
                }
            } while (szFileContents != null);
            reader.close();
        } catch (java.io.FileNotFoundException e) {
            LOGGER.info("File not founded!");
            return;
        } catch (java.io.IOException e) {
            LOGGER.info("File IO Exception");
            return;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (java.io.IOException e) {
                LOGGER.info("File IO Exception");
            }
        }
        LOGGER.info("Banco de dados carregado");
    }

    public boolean carregarBancoDeDadosDoFTP() {

        boolean ok = false;

        FTPClient client = inicializarClienteFTP();

        for (String extensao : extensoes) {
            try {
                File isdb = new File("data/database" + extensao);
                if (!isdb.exists() && !isdb.isDirectory()) {
                    isdb.getParentFile().mkdir();
                    isdb.createNewFile();
                }
                String remoteFile = "LeitorBiometrico/data/database" + extensao;
                File localFile = new File("data/database" + extensao);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(localFile));
                boolean success = client.retrieveFile(remoteFile, out);
                out.close();
                if (success) {
                    LOGGER.info("Arquivo " + extensao + " baixado com sucesso.");
                }
                ok = success;
            } catch (IOException ex) {

            }
        }
        return ok;
    }

    public void uploadBancoDeDadosParaFTP() {

        for (String extensao : extensoes) {

            try {
                FTPClient client = inicializarClienteFTP();
                File localFile = new File("data/database" + extensao);
                String remoteFile = "LeitorBiometrico/data/database" + extensao;
                InputStream inputStream = new FileInputStream(localFile);
                boolean done = client.storeFile(remoteFile, inputStream);
                inputStream.close();
                if (done) {
                    LOGGER.info("Arquivo " + extensao + " enviado para FTP com sucesso.");
                }
            } catch (IOException ex) {
                LOGGER.error(ex);
            }
        }
    }

    public String verificarCadastroDeDigitais(String valor) {

        BufferedReader br = null;
        String resposta = "";

        File f = new File("data/database.FID");
        if (!f.exists() && !f.isDirectory()) {
            return resposta;
        }

        try {
            valor = valor.replace("[", "").replace("]", "");
            String[] cpfs = valor.split(" ");
            ArrayList<String> repositorio = new ArrayList<>();
            br = new BufferedReader(new FileReader("data/database.FID"));
            String line;

            while ((line = br.readLine()) != null) {
                repositorio.add(line);
            }
            br.close();
            for (String repo : repositorio) {
                for (String cpf : cpfs) {
                    if (repo.equals(cpf)) {
                        resposta = resposta + cpf + ",";
                    }
                }
            }
            return resposta;
        } catch (FileNotFoundException ex) {
            LOGGER.error(ex);
        } catch (IOException ex) {
            LOGGER.error(ex);
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                LOGGER.error(ex);
            }
        }
        return resposta;
    }

    private boolean cadastroDigitalExiste(String cpf) {
        try {
            BufferedReader br = null;
            br = new BufferedReader(new FileReader("data/database.FID"));
            String line;

            while ((line = br.readLine()) != null) {
                if (line.equals(cpf)) {
                    return true;
                }
            }
            br.close();
        } catch (IOException ex) {
            LOGGER.error(ex);
        }
        return false;
    }

    public FTPClient inicializarClienteFTP() {

        FTPClient client = new FTPClient();

        try {
            client.connect(this.host);
            client.login(this.user, this.password);
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
            return client;
        } catch (IOException ex) {
            LOGGER.error(ex);
        }
        return client;
    }

    private void carregarArquivoDeConfiguracao() {
        try {
            Properties prop = new Properties();
            InputStream input = null;
            
            String filename = "config.properties";
            input = Operacoes.class.getClassLoader().getResourceAsStream(filename);
            if (input == null) {
                LOGGER.info("Sorry, unable to find " + filename);
                return;
            }

            prop.load(input);
            this.host = prop.getProperty("host");
            this.user = prop.getProperty("user");
            this.password = prop.getProperty("password");
            this.databasePath = prop.getProperty("database");
            this.user = decrypt(this.user);
            this.password = decrypt(this.password);

        } catch (FileNotFoundException ex) {
            LOGGER.error(ex);
        } catch (IOException ex) {
            LOGGER.error(ex);
        } catch (Exception ex) {
            LOGGER.error(ex);
        }
    }

    public static String decrypt(String encryptedData) throws Exception {
        Key key = generateKey();
        Cipher c = Cipher.getInstance(ALGO);
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decordedValue = Base64.getDecoder().decode(encryptedData);
        byte[] decValue = c.doFinal(decordedValue);
        return new String(decValue);
    }

    private static Key generateKey() throws Exception {
        return new SecretKeySpec(KEY_VALUE, ALGO);
    }
}
