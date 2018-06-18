package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.*;

/**
 * @author ken.lj
 * @date 02/04/2018
 */
public class JsonUtils {

    public static void writeObject(Object obj, PrintWriter writer) throws IOException {
        SerializeWriter out = new SerializeWriter();
        JSONSerializer serializer = new JSONSerializer(out);
        serializer.config(SerializerFeature.WriteEnumUsingToString, true);
        serializer.write(obj);
        out.writeTo(writer);
        out.close();
        writer.println();
    }

    public static void writeBytes(byte[] b, PrintWriter writer) {
        writer.print(new String(b));
    }

    public static void flush(PrintWriter writer) {
        writer.flush();
    }
}
