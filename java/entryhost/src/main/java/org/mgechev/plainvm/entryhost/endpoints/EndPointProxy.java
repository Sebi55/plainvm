package org.mgechev.plainvm.entryhost.endpoints;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.log4j.Logger;
import org.mgechev.plainvm.entryhost.endpoints.pojos.EndPoint;
import org.mgechev.plainvm.entryhost.endpoints.pojos.EndPointScreenshots;
import org.mgechev.plainvm.entryhost.endpoints.pojos.VmData;
import org.mgechev.plainvm.entryhost.messages.actions.ClientRequest;
import org.mgechev.plainvm.entryhost.messages.responses.ScreenshotUpdate;
import org.mgechev.plainvm.entryhost.messages.responses.Update;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

public class EndPointProxy extends Thread {

    private InetSocketAddress address;
    private Socket socket;
    private Thread reader;
    private Gson gson;
    private Logger log = Logger.getLogger(getClass());
    private EndPoint endPointPojo;
    private EndPointScreenshots endPointScreenshotsPojo;
    
    public EndPointProxy(InetSocketAddress address) {
        this.address = address;
        this.gson = new Gson();
        this.endPointPojo = new EndPoint(address.getHostName());
    }
    
    public EndPoint getEndPointPojo() {
        return endPointPojo;
    }
    
    public void connect() throws UnknownHostException, IOException {
        socket = new Socket(address.getHostName(), address.getPort());
        socket.setKeepAlive(true);
        startReading();
    }
    
    public void sendMessage(ClientRequest message) throws IOException {
        OutputStream os = socket.getOutputStream();
        os.write(gson.toJson(message).getBytes());
        os.flush();
    }
    
    public void pollForUpdate() throws IOException {
        ClientRequest action = new ClientRequest();
        action.needResponse = false;
        action.type = "update";
        sendMessage(action);
    }
    
    private void startReading() {
        try {
            reader = new Thread(new SocketReader(socket.getInputStream()));
            reader.start();
        } catch (IOException e) {
            log.error("Error while reading from the socket");
        }
    }
    
    public void handleUpdate(JsonObject data) {
        Update result = new Update(data);
        List<VmData> changed = endPointPojo.updateVms(result.data);
        if (changed.size() > 0) {
            EndPoint changedEndPoint = new EndPoint(endPointPojo.host);
            changedEndPoint.vms = changed;
            EndPointCollection.INSTANCE.updateEndPoint(changedEndPoint);
        }
    }
    
    public void handleScreenshotUpdate(JsonObject data) {
        ScreenshotUpdate result = new ScreenshotUpdate(data);
        List<VmData> changed = endPointScreenshotsPojo.updateVms(result.data);
        EndPointScreenshots changedScreenShots = new EndPointScreenshots(endPointScreenshotsPojo.host);
        changedScreenShots.vms = changed;
        EndPointCollection.INSTANCE.updateEndPoint(changedScreenShots);
    }
    
    private class SocketReader implements Runnable {
        private InputStream stream;
        private JsonReader reader;
        
        public SocketReader(InputStream stream) {
            this.stream = stream;
        }
        
        public void run() {
            log.info("Start reading from the given input stream");
            try {
                while (socket.isBound()) {
                    this.reader = new JsonReader(new InputStreamReader(stream, "UTF-8"));
                    JsonParser parser = new JsonParser();
                    JsonElement root = parser.parse(reader);
                    JsonObject obj = root.getAsJsonObject();
                    String type = obj.get("type").getAsString();
                    if (type.equals("update")) {
                        handleUpdate(obj);
                    } else if (type.equals("screenshot-update")) {
                        handleScreenshotUpdate(obj);
                    }
                }
            } catch (JsonIOException e) {
                //destroyEndPoint();
                log.error("Json IO exception while reading from the socket");
            } catch (JsonSyntaxException e) {
                //destroyEndPoint();
                log.error("Json syntax exception while reading from the socket");
            } catch (IOException e) {
                //destroyEndPoint();
                log.error("Error while reading from the socket");
            }
        }
    }
    
}
