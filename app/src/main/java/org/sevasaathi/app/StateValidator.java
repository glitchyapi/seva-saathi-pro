package org.sevasaathi.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Shared validation for on-device saves and portable backups. */
public final class StateValidator {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static JSONObject parse(String text) throws Exception {
        if (text == null || text.getBytes("UTF-8").length > MAX_BYTES) throw new Exception("Journal size limit");
        JSONObject s = new JSONObject(text);
        if (s.getInt("schema") != 1) throw new Exception("Unsupported journal format");
        if (!Arrays.asList("en", "hi").contains(s.getString("language"))) throw new Exception("Language");
        if (s.getJSONObject("profile").getString("name").length() > 80) throw new Exception("Name");
        int goal = s.getInt("goal");
        if (goal < 1 || goal > 10080) throw new Exception("Intention");
        s.getBoolean("autoUpdate");
        JSONArray es = s.getJSONArray("entries");
        if (es.length() > 10000) throw new Exception("Entry limit");
        Set<String> ids = new HashSet<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        for (int i=0; i<es.length(); i++) {
            JSONObject e = es.getJSONObject(i);
            String id=e.getString("id"), title=e.getString("title"), date=e.getString("date");
            if (id.isEmpty() || id.length()>80 || !ids.add(id)) throw new Exception("Entry ID");
            if (title.trim().isEmpty() || title.length()>120) throw new Exception("Title");
            if (!Arrays.asList("bhandara", "cleanliness", "literature", "community", "other").contains(e.getString("category"))) throw new Exception("Category");
            if (!date.matches("\\d{4}-\\d{2}-\\d{2}") || !format.format(format.parse(date)).equals(date)) throw new Exception("Date");
            double m=e.getDouble("minutes");
            if (m<1 || m>1440 || m!=Math.floor(m)) throw new Exception("Duration");
            if (e.getString("note").length()>1500) throw new Exception("Note");
        }
        return s;
    }
    public static JSONObject empty() throws Exception {
        return new JSONObject("{\"schema\":1,\"language\":\"en\",\"profile\":{\"name\":\"\"},\"goal\":120,\"autoUpdate\":true,\"favorite\":false,\"entries\":[],\"preview\":false}");
    }
    private StateValidator() {}
}
