package com.practice.phuc.client;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MyApp";

    LinearLayout loginPanel, chatPanel;
    EditText editTextUserName, editTextAddress;
    Button buttonConnect;
    TextView chatMsg, textPort;
    EditText editTextSay;
    Button buttonSend;
    Button buttonDisconnect;
    String msgLog = "";
    ChatClientThread chatClientThread = null;

    View.OnClickListener buttonDisconnectOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (chatClientThread == null) {
                return;
            }
            chatClientThread.disconnect();
        }
    };

    View.OnClickListener buttonSendOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (editTextSay.getText().toString().equals("")) {
                return;
            }

            if (chatClientThread == null) {
                return;
            }

            chatClientThread.sendMsg(editTextSay.getText().toString() + "\n");
        }
    };

    private String SERVICE_NAME = "ProTube_Client";
    private String SERVICE_TYPE = "_http._tcp.";
    private InetAddress hostAddress;
    private int hostPort;

    View.OnClickListener buttonConnectOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            String textUserName = editTextUserName.getText().toString();
            if (textUserName.equals("")) {
                Toast.makeText(MainActivity.this, "Enter User Name", Toast.LENGTH_LONG).show();
                return;
            }

            String textAddress = editTextAddress.getText().toString();
            if (textAddress.equals("")) {
                editTextAddress.setText(hostAddress.toString());
                return;
            }

            msgLog = "";
            chatMsg.setText(msgLog);
            loginPanel.setVisibility(View.GONE);
            chatPanel.setVisibility(View.VISIBLE);

            chatClientThread = new ChatClientThread(textUserName, hostAddress.getHostAddress(), hostPort);
            chatClientThread.start();
        }

    };

    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        loginPanel = (LinearLayout) findViewById(R.id.loginpanel);
        chatPanel = (LinearLayout) findViewById(R.id.chatpanel);

        editTextUserName = (EditText) findViewById(R.id.username);
        editTextAddress = (EditText) findViewById(R.id.address);
        textPort = (TextView) findViewById(R.id.port);
        buttonConnect = (Button) findViewById(R.id.connect);
        textPort.setText("port: " + hostPort);
        editTextAddress.setText("Still Searching...");
        buttonDisconnect = (Button) findViewById(R.id.disconnect);
        chatMsg = (TextView) findViewById(R.id.chatmsg);

        buttonConnect.setOnClickListener(buttonConnectOnClickListener);
        buttonDisconnect.setOnClickListener(buttonDisconnectOnClickListener);

        editTextSay = (EditText) findViewById(R.id.say);
        buttonSend = (Button) findViewById(R.id.send);

        buttonSend.setOnClickListener(buttonSendOnClickListener);

        initDiscoveryListener();
    }

    @Override
    protected void onPause() {
        if (mNsdManager != null) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mNsdManager != null) {
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        }
    }

    private void initDiscoveryListener(){
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.d(TAG, "onStartDiscoveryFailed: error code: " + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.d(TAG, "onStopDiscoveryFailed: error code: " + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "onDiscoveryStarted: " + serviceType);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "onDiscoveryStopped: " + serviceType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "onServiceFound: service name: " + serviceInfo.getServiceName());
                Log.d(TAG, "onServiceFound: port: " + serviceInfo.getPort());

                if (!serviceInfo.getServiceType().equals(SERVICE_TYPE)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(TAG, "Unknown Service Type: " + serviceInfo.getServiceType());

                } else if (serviceInfo.getServiceName().equals(SERVICE_NAME)) {
                    // The name of the service tells the user what they'd be
                    // connecting to. It could be "Bob's Chat App".
                    Log.d(TAG, "Same machine: " + SERVICE_NAME);

                } else {
                    Log.d(TAG, "Diff Machine : " + serviceInfo.getServiceName());
                    // connect to the service and obtain serviceInfo
                    mNsdManager.resolveService(serviceInfo, new CustomResolveListener());
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "onServiceLost: " + serviceInfo.getServiceName());
            }
        };
    }

    private class ChatClientThread extends Thread {
        String name;
        String dstAddress;
        int dstPort;

        String msgToSend = "";
        boolean goOut = false;

        ChatClientThread(String name, String address, int port) {
            this.name = name;
            dstAddress = address;
            dstPort = port;
        }

        @Override
        public void run() {
            Socket socket = null;

            //Getting Local address
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

            JSONObject jsonData = new JSONObject();

            try {
                jsonData.put("request", "request");
                jsonData.put("ipAddress", ip);
                jsonData.put("clientName", name);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("nsdServicejsontag", "can't put request");
                return;
            }

            DataOutputStream dataOutputStream = null;
            DataInputStream dataInputStream = null;

            try {
                socket = new Socket(dstAddress, dstPort);
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());
                dataOutputStream.writeUTF(jsonData.toString());
                dataOutputStream.flush();

                while (!goOut) {
                    if (dataInputStream.available() > 0) {
                        msgLog += dataInputStream.readUTF();

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                chatMsg.setText(msgLog);
                            }
                        });
                    }

                    if(!msgToSend.equals("")){
                        dataOutputStream.writeUTF(msgToSend);
                        dataOutputStream.flush();
                        msgToSend = "";
                    }
                }

            } catch (UnknownHostException e) {
                e.printStackTrace();
                final String eString = e.toString();
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                final String eString = e.toString();
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loginPanel.setVisibility(View.VISIBLE);
                        chatPanel.setVisibility(View.GONE);
                    }
                });
            }
        }

        private void sendMsg(String msg){
            msgToSend = msg;
        }

        private void disconnect(){
            goOut = true;
        }
    }

    private class CustomResolveListener implements NsdManager.ResolveListener {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e(TAG, "Resolve failed " + errorCode);
            Log.e(TAG, "serivce = " + serviceInfo);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "Resolve Succeeded. " + serviceInfo);

            if (serviceInfo.getServiceName().equals(SERVICE_NAME)) {
                Log.d(TAG, "Same IP.");
                return;
            }

            // Obtain port and IP
            hostPort = serviceInfo.getPort();
            hostAddress = serviceInfo.getHost();

            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    editTextAddress.setText(hostAddress.getHostAddress());
                }
            });
            textPort.setText("port: " + hostPort);
            Toast.makeText(MainActivity.this, "host address = " + hostAddress.getHostAddress(), Toast.LENGTH_SHORT).show();
        }
    }
}
