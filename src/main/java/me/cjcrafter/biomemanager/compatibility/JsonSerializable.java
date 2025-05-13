package me.cjcrafter.biomemanager.compatibility;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public interface JsonSerializable<T> {

    Gson gson = new Gson();

    JsonObject serialize();

    T deserialize(JsonObject json);

}
