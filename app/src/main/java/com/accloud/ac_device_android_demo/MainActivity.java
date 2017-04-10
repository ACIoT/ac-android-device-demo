package com.accloud.ac_device_android_demo;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.accloud.ac_device_android_demo.config.Config;
import com.accloud.ac_device_android_demo.controller.Light;
import com.accloud.ac_device_android_demo.utils.CrashHandler;
import com.accloud.ac_device_android_demo.utils.Pop;
import com.accloud.clientservice.AC;
import com.accloud.common.ACConfig;
import com.accloud.common.ACDeviceMsg;
import com.accloud.service.ACConnectChangeListener;
import com.accloud.service.ACMsgHandler;
import com.accloud.utils.ACUtils;
import com.accloud.utils.PreferencesUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class MainActivity extends Activity implements ACMsgHandler, ACConnectChangeListener {

    private TextView online;
    private TextView receive;
    private TextView physicalDeviceId;
    private Button openLight;
    private Button closeLight;
    private Spinner protocolSpinner;

    boolean isConnected;
    int protocol;

    private static final int OFF = 0;
    private static final int ON = 1;
    //设备控制类型 FROM_APP:通过APP等终端进行控制 FROM_SWITCH:通过设备物理按键进行控制
    private static final int FROM_APP = 0;
    private static final int FROM_SWITCH = 1;

    public static final int BINARY = 0;
    public static final int JSON = 1;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AC.init(this, ACUtils.getMacOrIMEI(this));

        AC.setConnectListener(this);
        AC.handleMsg(this);

        initView();
        openLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Light.turnLightOn();
                reportLightMsg(ON, FROM_SWITCH);
            }
        });
        closeLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Light.turnLightOff();
                reportLightMsg(OFF, FROM_SWITCH);
            }
        });

        CrashHandler.getInstance().init(this);
    }

    public void initView() {
        online = (TextView) findViewById(R.id.main_online);
        receive = (TextView) findViewById(R.id.main_receive);
        physicalDeviceId = (TextView) findViewById(R.id.physicalDeviceId);
        openLight = (Button) findViewById(R.id.openLight);
        closeLight = (Button) findViewById(R.id.closeLight);
        protocolSpinner = (Spinner) findViewById(R.id.protocol);

        physicalDeviceId.setText(ACConfig.getInstance(this).getPhysicalDeviceId());
        //启动app的时候没有打开wifi和移动网络
        if (!ACUtils.isNetworkConnected())
            online.setText(Html.fromHtml("在线状态：<font color=\"#48F24F\">请打开您的网络连接</font>"));
        initSpinner();
    }

    //选择通讯格式,二进制/json
    private void initSpinner() {
        protocolSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"二进制", "JSON"}));
        switch (PreferencesUtils.getInt("protocol", BINARY)) {
            case BINARY:
                protocolSpinner.setSelection(BINARY);
                break;
            case JSON:
                protocolSpinner.setSelection(JSON);
                break;
        }
        protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0)
                    protocol = BINARY;
                else
                    protocol = JSON;
                PreferencesUtils.putInt("protocol", protocol);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                protocol = PreferencesUtils.getInt("protocol", BINARY);
            }
        });
    }

    @Override
    public void handleMsg(ACDeviceMsg reqMsg, ACDeviceMsg respMsg) {
        //代表下发消息类型，云端控制或者直连控制
        if (reqMsg.getType() == ACDeviceMsg.OpType.CLOUD)
            receive.append("[cloud]");
        else
            receive.append("[direct]");

        //实际开发中只需要选择其中一种消息格式
        switch (protocol) {
            case BINARY:
                receive.append("-->" + reqMsg.getMsgCode() + ":" + Arrays.toString(reqMsg.getPayload()) + "\n");
                handleBinary(reqMsg, respMsg);
                break;
            case JSON:
                receive.append("-->" + reqMsg.getMsgCode() + ":" + new String(reqMsg.getPayload()) + "\n");
                try {
                    handleJson(reqMsg, respMsg);
                } catch (JSONException e) {
                    Pop.popToast(this, e.toString());
                }
                break;
        }
    }

    public void handleBinary(ACDeviceMsg reqMsg, ACDeviceMsg respMsg) {
        switch (reqMsg.getMsgCode()) {
            case Config.CODE_SWITCH_REQ:
                //请求消息体
                byte[] payload = reqMsg.getPayload();
                if (payload[0] == ON) {
                    if (Light.turnLightOn()) {
                        respMsg.setPayload(new byte[]{1, 0, 0, 0});    //开灯成功
                        reportLightMsg(ON, FROM_APP);                  //上报开关状态
                    } else
                        respMsg.setPayload(new byte[]{0, 0, 0, 0});    //开灯失败
                } else {
                    if (Light.turnLightOff()) {
                        respMsg.setPayload(new byte[]{1, 0, 0, 0});    //关灯成功
                        reportLightMsg(OFF, FROM_APP);                 //上报开关状态
                    } else
                        respMsg.setPayload(new byte[]{0, 0, 0, 0});    //关灯失败
                }
                respMsg.setMsgCode(Config.CODE_SWITCH_RESP);
                break;
        }
    }

    //JSON格式的resp不需要设置msgCode
    private void handleJson(ACDeviceMsg reqMsg, ACDeviceMsg respMsg) throws JSONException {
        switch (reqMsg.getMsgCode()) {
            case Config.CODE_JSON:
                //请求消息体
                JSONObject req = new JSONObject(reqMsg.getJsonPayload());
                //请求操作类型，关灯或开灯
                int value = req.getInt("switch");
                //响应消息体
                JSONObject resp = new JSONObject();
                if (value == ON) {
                    if (Light.turnLightOn()) {
                        resp.put("result", true);
                        respMsg.setJsonPayload(resp.toString());    //开灯成功
                        reportLightMsg(ON, FROM_APP);               //上报开关状态
                    } else {
                        resp.put("result", false);
                        respMsg.setJsonPayload(resp.toString());    //开灯失败
                    }
                } else if (value == OFF) {
                    if (Light.turnLightOff()) {
                        resp.put("result", true);
                        respMsg.setJsonPayload(resp.toString());    //关灯成功
                        reportLightMsg(OFF, FROM_APP);              //上报开关状态
                    } else {
                        resp.put("result", false);
                        respMsg.setJsonPayload(resp.toString());    //关灯失败
                    }
                }
                break;
        }
    }

    @Override
    public void disconnect() {
        isConnected = false;
        online.setText(Html.fromHtml("在线状态：<font color=\"#48F24F\">不在线</font>"));
        Pop.popToast(this, "与云端断开连接，请检查网络状态");
    }

    @Override
    public void connect() {
        isConnected = true;
        online.setText(Html.fromHtml("在线状态：<font color=\"#48F24F\">在线</font>"));
        Pop.popToast(this, "成功连接云端");
        //发送5min广播，便于app通过startAbleLink接口进行无线绑定
        AC.DeviceStartBc(ACConfig.getInstance(this).getPhysicalDeviceId());
    }

    /**
     * 上报数据到AbleCloud云端（以控制灯为例，实际开发中协议由开发者自己定义）
     *
     * @param status 开关状态，1代表开，0代表关
     * @param source 开关类型，0代表app控制，1代表本机控制
     */
    public void reportLightMsg(int status, int source) {
        ACDeviceMsg req = new ACDeviceMsg();
        req.setMsgCode(Config.CODE_REPORT);
        //注意：实际开发中请只选择以下其中一种消息格式
        switch (protocol) {
            case BINARY:
                req.setPayload(new byte[]{(byte) status, (byte) source, 0, 0});
                break;
            case JSON:
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("switch", status);
                    jsonObject.put("source", source);
                    req.setJsonPayload(jsonObject.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
        }
        //把数据真正上报给云端
        AC.reportDeviceMsg(req);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //如果APP在进入后台后依然想保持长链接的话，则不需要执行此行代码
        AC.DeviceSleep();
    }
}
