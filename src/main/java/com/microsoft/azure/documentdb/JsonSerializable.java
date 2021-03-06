package com.microsoft.azure.documentdb;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonSerializable {
    JSONObject propertyBag = null;
    private Logger logger = null;

    protected JsonSerializable() {
        this.propertyBag = new JSONObject();
    }

    /**
     * Constructor.
     * 
     * @param jsonString the json string that represents the JsonSerializable.
     */
    protected JsonSerializable(String jsonString) {
        this.propertyBag = new JSONObject(jsonString);
    }

    /**
     * Constructor.
     * 
     * @param jsonObject the json object that represents the JsonSerializable.
     */
    protected JsonSerializable(JSONObject jsonObject) {
        this.propertyBag = new JSONObject(jsonObject);
    }
    
    private static HashMap<String, Object> toMap(JSONObject object) throws JSONException {
        HashMap<String, Object> map = new HashMap<String, Object>();

        @SuppressWarnings("unchecked") // Using legacy API
        Iterator<String> keysItr = object.keys();
        while (keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    private static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    private static Object[] convertToObjectArray(Object array) {
        Class<?> ofArray = array.getClass().getComponentType();
        if (ofArray.isPrimitive()) {
            List<Object> ar = new ArrayList<Object>();
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                ar.add(Array.get(array, i));
            }
            return ar.toArray();
        } else {
            return (Object[]) array;
        }
    }

    private static void checkForValidPOJO(Class<?> c) {
        if (c.isAnonymousClass() || c.isLocalClass()) {
            throw new IllegalArgumentException(
                    String.format("%s can't be an anonymous or local class.", c.getName()));
        }
        if (c.isMemberClass() && !Modifier.isStatic(c.getModifiers())) {
            throw new IllegalArgumentException(
                    String.format("%s must be static if it's a member class.", c.getName()));
        }
    }

    protected Logger getLogger() {
        if (this.logger == null) {
            this.logger = Logger.getLogger(this.getClass().getPackage().getName());
        }

        return this.logger;
    }

    void onSave() {
    }

    /**
     * Returns the propertybag(JSONObject) in a hashMap
     *
     * @return the HashMap.
     */
    public HashMap<String, Object> getHashMap() {
        return JsonSerializable.toMap(this.propertyBag);
    }

    /**
     * Checks whether a property exists.
     * 
     * @param propertyName the property to look up.
     * @return true if the property exists.
     */
    public boolean has(String propertyName) {
        return this.propertyBag.has(propertyName);
    }

    /**
     * Removes a value by propertyName.
     * 
     * @param propertyName the property to remove.
     */
    public void remove(String propertyName) {
        this.propertyBag.remove(propertyName);
    }

    /**
     * Sets the value of a property.
     * 
     * @param <T>          the type of the object.
     * @param propertyName the property to set.
     * @param value        the value of the property.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Object> void set(String propertyName, T value) {
        if (value == null) {
            // Sets null.
            this.propertyBag.put(propertyName, JSONObject.NULL);
        } else if (value instanceof Collection) {
            // Collection.
            JSONArray jsonArray = new JSONArray();
            this.internalSetCollection(propertyName, (Collection) value, jsonArray);
            this.propertyBag.put(propertyName, jsonArray);
        } else if (value.getClass().isArray()) {
            // Array.
            JSONArray jsonArray = new JSONArray();
            this.internalSetCollection(propertyName, Arrays.asList(JsonSerializable.convertToObjectArray(value)),
                    jsonArray);
            this.propertyBag.put(propertyName, jsonArray);
        } else if (value instanceof Number || value instanceof Boolean || value instanceof String
                || value instanceof JSONObject) {
            // JSONObject, number (includes int, float, double etc), boolean,
            // and string.
            this.propertyBag.put(propertyName, value);
        } else if (value instanceof JsonSerializable) {
            // JsonSerializable
            JsonSerializable castedValue = (JsonSerializable) value;
            if (castedValue != null) {
                castedValue.onSave();
            }
            this.propertyBag.put(propertyName, castedValue != null ? castedValue.propertyBag : null);
        } else {
            // POJO
            ObjectMapper mapper = new ObjectMapper();
            try {
                this.propertyBag.put(propertyName, new JSONObject(mapper.writeValueAsString(value)));
            } catch (IOException e) {
                throw new IllegalArgumentException("Can't serialize the object into the json string", e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void internalSetCollection(String propertyName, Collection<T> collection, JSONArray targetArray) {
            ObjectMapper mapper = null;

            for (T childValue : collection) {
                if (childValue == null) {
                    // Sets null.
                    targetArray.put(JSONObject.NULL);
                } else if (childValue instanceof Collection) {
                    // When T is also a Collection, use recursion.
                    JSONArray childArray = new JSONArray();
                    this.internalSetCollection(propertyName, (Collection) childValue, childArray);
                    targetArray.put(childArray);
            } else if (childValue instanceof Number || childValue instanceof Boolean || childValue instanceof String
                    || childValue instanceof JSONObject) {
                // JSONObject, Number (includes Int, Float, Double etc),
                // Boolean, and String.
                    targetArray.put(childValue);
                } else if (childValue instanceof JsonSerializable) {
                    // JsonSerializable
                JsonSerializable castedValue = (JsonSerializable) childValue;
                    castedValue.onSave();
                    targetArray.put(castedValue.propertyBag != null ? castedValue.propertyBag : new JSONObject());
                } else {
                    // POJO
                if (mapper == null)
                    mapper = new ObjectMapper();
                    try {
                        targetArray.put(new JSONObject(mapper.writeValueAsString(childValue)));
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Can't serialize the object into the json string", e);
                    }
                }
            }
    }

    /**
     * Gets a property value as Object.
     * 
     * @param propertyName the property to get.
     * @return the value of the property.
     */
    public Object get(String propertyName) {
        if (this.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            return this.propertyBag.get(propertyName);
        } else {
            return null;
        }
    }
    
    /**
     * Gets a string value.
     * 
     * @param propertyName the property to get.
     * @return the string value.
     */
    public String getString(String propertyName) {
        if (this.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            return this.propertyBag.getString(propertyName);
        } else {
            return null;
        }
    }

    /**
     * Gets a boolean value.
     * 
     * @param propertyName the property to get.
     * @return the boolean value.
     */
    public Boolean getBoolean(String propertyName) {
        if (this.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            return new Boolean(this.propertyBag.getBoolean(propertyName));
        } else {
            return null;
        }
    }

    /**
     * Gets an integer value.
     * 
     * @param propertyName the property to get.
     * @return the boolean value
     */
    public Integer getInt(String propertyName) {
        if (this.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            return new Integer(this.propertyBag.getInt(propertyName));
        } else {
            return null;
        }
    }

    /**
     * Gets a long value.
     * 
     * @param propertyName the property to get.
     * @return the long value
     */
    public Long getLong(String propertyName) {
        if (this.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            return new Long(this.propertyBag.getLong(propertyName));
        } else {
            return null;
        }
    }

    /**
     * Gets a double value.
     * 
     * @param propertyName the property to get.
     * @return the double value.
     */
    public Double getDouble(String propertyName) {
        if (this.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            return new Double(this.propertyBag.getDouble(propertyName));
        } else {
            return null;
        }
    }

    /**
     * Gets an object value.
     * 
     * @param <T>          the type of the object.
     * @param propertyName the property to get.
     * @param c            the class of the object. If c is a POJO class, it must be a member (and not an anonymous or local)
     *                     and a static one.
     * @return the object value.
     */
    public <T extends Object> T getObject(String propertyName, Class<T> c) {
        if (this.propertyBag.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            JSONObject jsonObj = this.propertyBag.getJSONObject(propertyName);
            if (Number.class.isAssignableFrom(c) || String.class.isAssignableFrom(c)
                    || Boolean.class.isAssignableFrom(c) || Object.class == c) {
                // Number, String, Boolean
                return c.cast(jsonObj);
            } else if (Enum.class.isAssignableFrom(c)) {
                try {
                    c.cast(c.getMethod("valueOf", String.class).invoke(null, String.class.cast(jsonObj)));
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                        | NoSuchMethodException | SecurityException | JSONException e) {
                    throw new IllegalStateException("Failed to create enum.", e);
                }
            } else if (JsonSerializable.class.isAssignableFrom(c)) {
                try {
                    return c.getConstructor(String.class).newInstance(jsonObj.toString());
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    throw new IllegalStateException("Failed to instantiate class object.", e);
                }
            } else {
                // POJO
                JsonSerializable.checkForValidPOJO(c);

                try {
                    return new ObjectMapper().readValue(jsonObj.toString(), c);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to get POJO.", e);
                }
            }
        }

        return null;
    }

    /**
     * Gets an object collection.
     * 
     * @param <T>          the type of the objects in the collection.
     * @param propertyName the property to get
     * @param c            the class of the object. If c is a POJO class, it must be a member (and not an anonymous or local)
     *                     and a static one.
     * @return the object collection.
     */
    public <T extends Object> Collection<T> getCollection(String propertyName, Class<T> c) {
        if (this.propertyBag.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            JSONArray jsonArray = this.propertyBag.getJSONArray(propertyName);
            Collection<T> result = new ArrayList<T>();
            ObjectMapper mapper = null;
            boolean isBaseClass = false;
            boolean isEnumClass = false;
            boolean isJsonSerializable = false;

            // Check once.
            if (Number.class.isAssignableFrom(c) || String.class.isAssignableFrom(c)
                    || Boolean.class.isAssignableFrom(c) || Object.class == c) {
                isBaseClass = true;
            } else if (Enum.class.isAssignableFrom(c)) {
                isEnumClass = true;
            } else if (JsonSerializable.class.isAssignableFrom(c)) {
                isJsonSerializable = true;
            } else {
                JsonSerializable.checkForValidPOJO(c);
                mapper = new ObjectMapper();
            }

            for (int i = 0; i < jsonArray.length(); i++) {
                if (isBaseClass) {
                    // Number, String, Boolean 
                    result.add(c.cast(jsonArray.get(i)));
                } else if (isEnumClass) {
                    try {
                        result.add(c.cast(c.getMethod("valueOf", String.class).invoke(null,
                                String.class.cast(jsonArray.get(i)))));
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                            | NoSuchMethodException | SecurityException | JSONException e) {
                        throw new IllegalStateException("Failed to create enum.", e);
                    }
                } else if (isJsonSerializable) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    // JsonSerializable
                    try {
                        result.add(c.getConstructor(String.class).newInstance(jsonObject.toString()));
                    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                            | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                        throw new IllegalStateException("Failed to instantiate class object.", e);
                    }
                } else {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    // POJO
                    try {
                        result.add(mapper.readValue(jsonObject.toString(), c));
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to get POJO.", e);
                    }
                }
            }

            return result;
        }

        return null;
    }

    /**
     * Gets a JSONObject.
     * 
     * @param propertyName the property to get.
     * @return the JSONObject.
     */
    public JSONObject getObject(String propertyName) {
        if (this.propertyBag.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            JSONObject jsonObj = this.propertyBag.getJSONObject(propertyName);
            return jsonObj;
        }
        return null;
    }

    /**
     * Gets a JSONObject collection.
     * 
     * @param propertyName the property to get.
     * @return the JSONObject collection.
     */
    public Collection<JSONObject> getCollection(String propertyName) {
        Collection<JSONObject> result = null;
        if (this.propertyBag.has(propertyName) && !this.propertyBag.isNull(propertyName)) {
            result = new ArrayList<JSONObject>();
            JSONArray jsonArray = this.propertyBag.getJSONArray(propertyName);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                result.add(jsonObject);
            }
        }

        return result;    
    }

    /**
     * Gets the value of a property identified by an array of property names that forms the path.
     * 
     * @param propertyNames that form the path to the the property to get.
     * @return the value of the property.
     */
    public Object getObjectByPath(Collection<String> propertyNames) {
        JSONObject propBag = this.propertyBag;
        Object value = null;
        String propertyName = null;
        Integer matchedProperties = 0;
        Iterator<String> iterator = propertyNames.iterator();
        if (iterator.hasNext()) {
            do {
                propertyName = iterator.next();
                if (propBag.has(propertyName)) {
                    matchedProperties++;
                    value = propBag.get(propertyName);
                    if (value.getClass() != JSONObject.class) {
                        break;
                    }
                    propBag = (JSONObject) value;
                } else {
                    break;
                }
            } while (iterator.hasNext());
            
            if (value != null && matchedProperties == propertyNames.size()) {
                return value;
            }
        }
        
        return null;
    }
    
    /**
     * Converts to an Object (only POJOs and JSONObject are supported).
     * 
     * @param <T> the type of the object.
     * @param c   the class of the object, either a POJO class or JSONObject. If c is a POJO class, it must be a member
     *            (and not an anonymous or local) and a static one.
     * @return the POJO.
     */
    public <T extends Object> T toObject(Class<T> c) {
        if (JsonSerializable.class.isAssignableFrom(c) || String.class.isAssignableFrom(c)
                || Number.class.isAssignableFrom(c) || Boolean.class.isAssignableFrom(c)) {
            throw new IllegalArgumentException("c can only be a POJO class or JSONObject");
        }
        if (JSONObject.class.isAssignableFrom(c)) {
            // JSONObject
            if (JSONObject.class != c) {
                throw new IllegalArgumentException("We support JSONObject but not its sub-classes.");
            }
            return c.cast(this.propertyBag);
        } else {
            // POJO
            JsonSerializable.checkForValidPOJO(c);
            try {
                return new ObjectMapper().readValue(this.toString(), c);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to get POJO.", e);
            }
        }
    }

    /**
     * Converts to a JSON string.
     * 
     * @return the JSON string.
     */
    public String toString() {
        this.onSave();
        return this.propertyBag.toString();
    }

    /**
     * Converts to a formatted JSON string.
     * 
     * @param indentFactor the indent factor.
     * @return the formatted JSON string.
     * @throws JSONException the json exception.
     */
    public String toString(int indentFactor) throws JSONException {
        this.onSave();
        return this.propertyBag.toString(indentFactor);
    }
}
