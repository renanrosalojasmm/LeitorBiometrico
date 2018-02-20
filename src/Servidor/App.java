/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Servidor;

import LeitorBiometrico.Operacoes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.log4j.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author renan.rosa
 */
public class App {

    private static final int PORT = 8484;
    final static Logger logger = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {

        try {

            ServerSocket server = new ServerSocket(PORT);
            logger.info("MiniServer active " + PORT);
            while (true) {
                new ThreadSocket(server.accept());
            }
        } catch (IOException ex) {

        }
    }
}

class ThreadSocket extends Thread {
    final static Logger logger = Logger.getLogger(ThreadSocket.class.getName());
    private final Socket insocket;

    ThreadSocket(Socket insocket) {
        this.insocket = insocket;
        this.start();
    }

    @Override
    public void run() {

        try {
            InputStream is = insocket.getInputStream();
            PrintWriter out = new PrintWriter(insocket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            String line = "";
            line = in.readLine();
            int postDataI = -1;

            while ((line = in.readLine()) != null && (line.length() != 0)) {
                if (line.indexOf("Content-Length:") > -1) {
                    postDataI = new Integer(
                            line.substring(line.indexOf("Content-Length:") + 16,
                                    line.length())).intValue();
                }
            }
            String postData = "";
            if (postDataI > 0) {
                char[] charArray = new char[postDataI];
                in.read(charArray, 0, postDataI);
                postData = new String(charArray);

                String[] postParam = postData.split("&");

                if (postParam.length == 1) {
                    String entrypoint = postParam[0];
                    entrypoint = entrypoint.replace("{", "").replace("}", "").replace("parametro", "").replace(",", "").replace("\"", "");;
                    String[] partes = entrypoint.split(":");
                    String comando = partes[1];
                    String parametro = partes[2];

                    logger.info("Comando recebido: " + comando);
                    logger.info("Parametro recebido: " + parametro);
                    Operacoes operacoes = new Operacoes();

                    if (operacoes.verificarDispositivo()) {
                        out.println("HTTP/1.0 418 OK");
                        out.println("Content-Type: text/html; charset=utf-8");
                        out.println("Access-Control-Allow-Origin: *");
                        out.println("Server: MINISERVER");
                        out.println("");
                        JSONObject json = new JSONObject();
                        json.put("resposta", "device_error");
                        out.println(json);
                    } else {
                        operacoes.carregarBancoDeDadosDoFTP();
                        operacoes.carregarDB();

                        if (comando.equals("cadastrardigital")) {
                            String cadastrarDigital = operacoes.cadastrarDigital(parametro);
                            out.println("HTTP/1.0 200 OK");
                            out.println("Content-Type: text/html; charset=utf-8");
                            out.println("Access-Control-Allow-Origin: *");
                            out.println("Server: MINISERVER");
                            out.println("");
                            JSONObject json = new JSONObject();
                            json.put("resposta", cadastrarDigital);
                            out.println(json);
                        }
                        if (comando.equals("verificardigital")) {

                            String verificarDigital = operacoes.verificarDigital(parametro);
                            logger.info(verificarDigital);
                            out.println("HTTP/1.0 200 OK");
                            out.println("Content-Type: text/html; charset=utf-8");
                            out.println("Access-Control-Allow-Origin: *");
                            out.println("Server: MINISERVER");
                            out.println("");
                            JSONObject json = new JSONObject();
                            json.put("resposta", verificarDigital);
                            out.println(json);
                        }

                        if (comando.equals("verificarcadastrodedigitais")) {
                            String verificarDigital = operacoes.verificarCadastroDeDigitais(parametro);
                            logger.info(!verificarDigital.equals("") ? "Cadastro encontrado" : "Cadastro não encontrado");
                            out.println("HTTP/1.0 200 OK");
                            out.println("Content-Type: text/html; charset=utf-8");
                            out.println("Access-Control-Allow-Origin: *");
                            out.println("Server: MINISERVER");
                            out.println("");
                            JSONObject json = new JSONObject();
                            json.put("resposta", verificarDigital);
                            out.println(json);
                        }
                    }
                }
            }
            out.close();
            insocket.close();
        } catch (IOException | NumberFormatException | JSONException ex) {

        }
    }
}
