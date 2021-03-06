package main.java;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Controller {

    public HBox transferPannel;
    @FXML

    TextField setLoginField;

    @FXML
    TextField setNicknameField;

    @FXML
    TextField setPasswordField;


    @FXML
    ListView<String> servertFileList;

    @FXML
    ListView<String> clientFileList;

    @FXML
    HBox regPanel;

    @FXML
    HBox upperPanel;

    @FXML
    TextField loginField;

    @FXML
    PasswordField passwordField;

    @FXML
    Button upButClient;

    @FXML
    Button upButServer;


    Socket socket;
    DataInputStream in;
    DataOutputStream out;
    SocketChannel channel;


    private String clientFileName;
    private String serverFileName;
    private final String rootPath = "client/files";
    private List<String> path = new ArrayList<>();

    private String nick;



    final String IP_ADRESS = "localhost";
    final int PORT = 8189;

    private boolean isAuthorised;

    public void  setAuthorised(boolean isAuthorised){
        this.isAuthorised = isAuthorised;
        if(!isAuthorised){
            upperPanel.setVisible(true);
            upperPanel.setManaged(true);
            transferPannel.setVisible(false);
            transferPannel.setManaged(false);
            regPanel.setManaged(false);
            regPanel.setVisible(false);
        }else {
            upperPanel.setVisible(false);
            upperPanel.setManaged(false);
            regPanel.setManaged(false);
            regPanel.setVisible(false);
            transferPannel.setVisible(true);
            transferPannel.setManaged(true);
        }
    }

    public void  setRegistration(boolean isAuthorised){
        this.isAuthorised = isAuthorised;
        if(!isAuthorised){
            upperPanel.setVisible(false);
            upperPanel.setManaged(false);
            regPanel.setManaged(false);
            regPanel.setVisible(false);
            transferPannel.setVisible(true);
            transferPannel.setManaged(true);
        }else {
            upperPanel.setVisible(true);
            upperPanel.setManaged(true);
            regPanel.setManaged(false);
            regPanel.setVisible(false);
            transferPannel.setVisible(false);
            transferPannel.setManaged(false);
        }
    }

    public void connect() {
        try {
            socket = new Socket(IP_ADRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            SocketChannel channel = socket.getChannel();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            String srt = in.readUTF();
                            if (srt.equals("/authok")) {
                                setAuthorised(true);
                                File file = new File(rootPath);
                                file.mkdirs();
                                path.add(rootPath);
                                break;
                            }else if (srt.equals("/regok")) {
                                setRegistration(true);
                            }
                        }
                        while (true) {
                            broadcastClientFile(dirPath(path));

                            String str = in.readUTF();
                            if (str.startsWith("/")) {
                                if (str.equals("/serverclosed")) break;
                                if (str.startsWith("/serverfilelist ")) {
                                    String[] tokens = str.split(" ");
                                    Platform.runLater(() -> {
                                        servertFileList.getItems().clear();
                                        for (int i = 1; i < tokens.length; i++) {
                                            servertFileList.getItems().add(tokens[i]);
                                        }
                                    });
                                }

                                if(str.startsWith("/sendFileFromServer")){
                                    sendFileFromServer(str);
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        setAuthorised(false);
                    }
                }
            }).start();
        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    public void broadcastClientFile(String path){

        File file = new File(path);
        StringBuilder sb = new StringBuilder();

        String[] str = file.list();
        for (String fileName:str) {
            sb.append(fileName + " ");
        }
        String out = sb.toString();
        String[] tokens = out.split(" ");
        Platform.runLater(() -> {
            clientFileList.getItems().clear();
                for (int i = 0; i < tokens.length; i++) {
                    clientFileList.getItems().add(tokens[i]);
                }
        });

    }

    public void Dispose() {
        System.out.println("Отправляем сообщение на сервер о завершении работы");
        try {
            if (out != null) {
                out.writeUTF("/end");
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToAuth(ActionEvent event) {
        if(socket == null || socket.isClosed()){
            connect();
        }
        try {
            nick = loginField.getText();
            out.writeUTF("/auth " + nick + " " + passwordField.getText());
            out.flush();
            loginField.clear();
            passwordField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void regpanel(ActionEvent event) {
        upperPanel.setVisible(false);
        upperPanel.setManaged(false);
        regPanel.setManaged(true);
        regPanel.setVisible(true);
    }

    public void reg(ActionEvent event) {
        if(socket == null || socket.isClosed()){
            connect();
        }
        try {
            out.writeUTF("/reg " + setNicknameField.getText() + " " + setLoginField.getText() + " " +
                    setPasswordField.getText());
            out.flush();
            setNicknameField.clear();
            setLoginField.clear();
            setPasswordField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendFileToServer(ActionEvent actionEvent) {
        String filePath = dirPath(path) + "/" + clientFileName;
        File file = new File(filePath);
        if(file.getName() != null){
            String str = "/sendFileToServer " + clientFileName;
            try {
                out.writeUTF(str);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] buffer = new byte[8192];
            try(BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                BufferedOutputStream bos = new BufferedOutputStream(new DataOutputStream(socket.getOutputStream()));) {
                int x = 0;
                while (bis.available() > 0){
                    if((x = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, x);
                        bos.flush();
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public void upClientDir(ActionEvent actionEvent) {
        if(path.size() >1){
            path.remove(path.size() - 1);
            broadcastClientFile(dirPath(path));
        }
    }

    public void selectClientFile(MouseEvent mouseEvent) {
        path.add("/" + clientFileList.getSelectionModel().getSelectedItem());
        File file = new File(dirPath(path));
        if (!file.isDirectory()){
            path.remove(path.size() - 1);
        }else broadcastClientFile(dirPath(path));
        clientFileName = clientFileList.getSelectionModel().getSelectedItem();
    }

    private String dirPath(List<String> path) {
        StringBuilder sb = new StringBuilder();
        for (String s: path){
            sb.append(s);
        }
        String dirPath = sb.toString();
        return dirPath;
    }

    public void selectServerFile(MouseEvent mouseEvent) {
        serverFileName = servertFileList.getSelectionModel().getSelectedItem();
    }

    public void sendFileToClient(ActionEvent actionEvent) {
        String str = "/getFileFromServer " + serverFileName;
        try {
            out.writeUTF(str);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private void sendFileFromServer(String str) {
        String[] tockens = str.split(" ",2);
        String fileName = dirPath(path)  + "/" + tockens[1];
        File file = new File(fileName);
        if(!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try(FileOutputStream fos = new FileOutputStream(file);
        BufferedInputStream bis = new BufferedInputStream(socket.getInputStream())){
            int x;
            byte[] buffer = new byte[8192];
            while (( bis.available()) > 0){
                if((x = bis.read(buffer)) != -1){
                    System.out.println("X: " + x);
                    fos.write(buffer, 0, x);
                    fos.flush();
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        broadcastClientFile(dirPath(path));
    }

    public void deleteFileServer(ActionEvent actionEvent) {
        String str = "/delFile " + serverFileName;
        try {
            out.writeUTF(str);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteFileClient(ActionEvent actionEvent) {
        String filePath = dirPath(path) + "/" + clientFileName;
        File file = new File(filePath);
        file.delete();
        broadcastClientFile(dirPath(path));
    }

    public void back(ActionEvent actionEvent) {
        upperPanel.setVisible(true);
        upperPanel.setManaged(true);
        regPanel.setManaged(false);
        regPanel.setVisible(false);

    }
}
