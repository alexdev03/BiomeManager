package me.cjcrafter.biomemanager.compatibility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public interface JsonSerializable<T> {

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    JsonObject serialize();

    T deserialize(JsonObject json);

}
