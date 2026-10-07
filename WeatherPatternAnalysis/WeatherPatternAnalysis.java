import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Weather Pattern Analysis - Chennai (Java, no external libraries)
 *
 * Downloads daily weather from Open-Meteo (free, no API key), cleans it,
 * analyses seasonal patterns and correlations, and writes to ./output :
 *   cleaned_weather_data.csv, summary.txt, report.html (charts)
 *
 * Requires JDK 17+.
 * Run in VS Code:  click "Run" above main(), or in terminal:
 *   javac WeatherPatternAnalysis.java
 *   java WeatherPatternAnalysis
 * Optional: java WeatherPatternAnalysis --csv myfile.csv
 *   (CSV columns: date,temp_max,temp_min,temp_mean,rain_mm,wind_max)
 */
public class WeatherPatternAnalysis {

    static final String CITY = "Chennai";
    static final double LAT = 13.08, LON = 80.27;
    static final String START = "2023-01-01", END = "2025-12-31";
    static final String[] COLS = {"temp_max", "temp_min", "temp_mean", "rain_mm", "wind_max"};
    static final String[] API_KEYS = {"temperature_2m_max", "temperature_2m_min",
            "temperature_2m_mean", "precipitation_sum", "wind_speed_10m_max"};
    static final String[] SEASONS = {"Winter", "Summer", "Monsoon (SW)", "Monsoon (NE)"};
    static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    static List<LocalDate> dates = new ArrayList<>();
    static double[][] v = new double[5][]; // v[0]=max, 1=min, 2=mean, 3=rain, 4=wind

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("--csv")) readCsv(args[1]);
        else fetch();

        System.out.println("Rows loaded: " + dates.size());
        for (int c = 0; c < 5; c++) {
            System.out.println("Missing in " + COLS[c] + ": " + countNaN(v[c]));
            fillGaps(v[c]);
        }

        Files.createDirectories(Path.of("output"));
        writeCsv();
        analyse();
    }

    // ---------- Data loading ----------

    static void fetch() throws IOException, InterruptedException {
        String url = "https://archive-api.open-meteo.com/v1/archive?latitude=" + LAT
                + "&longitude=" + LON + "&start_date=" + START + "&end_date=" + END
                + "&daily=" + String.join(",", API_KEYS) + "&timezone=Asia%2FKolkata";
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200)
            throw new IOException("API error " + res.statusCode() + ": " + res.body());
        String json = res.body();

        for (String d : extractArray(json, "time")) dates.add(LocalDate.parse(d.replace("\"", "").trim()));
        for (int c = 0; c < 5; c++) {
            String[] parts = extractArray(json, API_KEYS[c]);
            v[c] = new double[parts.length];
            for (int i = 0; i < parts.length; i++) v[c][i] = parseNum(parts[i]);
        }
    }

    // Finds  "key":[ ... ]  in the JSON and returns the items
    static String[] extractArray(String json, String key) {
        int i = json.indexOf("\"" + key + "\":[");
        if (i < 0) throw new IllegalStateException("Key not found in response: " + key);
        int s = json.indexOf('[', i), e = json.indexOf(']', s);
        return json.substring(s + 1, e).split(",");
    }

    static double parseNum(String s) {
        s = s.trim();
        return (s.isEmpty() || s.equals("null")) ? Double.NaN : Double.parseDouble(s);
    }

    static void readCsv(String file) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(file));
        String[] header = lines.get(0).split(",");
        int[] idx = new int[5];
        int dateIdx = -1;
        for (int h = 0; h < header.length; h++) {
            String name = header[h].trim();
            if (name.equals("date")) dateIdx = h;
            for (int c = 0; c < 5; c++) if (name.equals(COLS[c])) idx[c] = h;
        }
        int n = lines.size() - 1;
        for (int c = 0; c < 5; c++) v[c] = new double[n];
        for (int r = 0; r < n; r++) {
            String[] p = lines.get(r + 1).split(",");
            dates.add(LocalDate.parse(p[dateIdx].trim().substring(0, 10)));
            for (int c = 0; c < 5; c++) v[c][r] = parseNum(p[idx[c]]);
        }
    }

    // ---------- Cleaning ----------

    static int countNaN(double[] a) {
        int n = 0;
        for (double x : a) if (Double.isNaN(x)) n++;
        return n;
    }

    // forward fill, then backward fill for any leading gaps
    static void fillGaps(double[] a) {
        for (int i = 1; i < a.length; i++) if (Double.isNaN(a[i])) a[i] = a[i - 1];
        for (int i = a.length - 2; i >= 0; i--) if (Double.isNaN(a[i])) a[i] = a[i + 1];
    }

    static void writeCsv() throws IOException {
        StringBuilder sb = new StringBuilder("date,temp_max,temp_min,temp_mean,rain_mm,wind_max\n");
        for (int i = 0; i < dates.size(); i++) {
            sb.append(dates.get(i));
            for (int c = 0; c < 5; c++) sb.append(',').append(String.format(Locale.ROOT, "%.2f", v[c][i]));
            sb.append('\n');
        }
        Files.writeString(Path.of("output/cleaned_weather_data.csv"), sb.toString());
    }

    // ---------- Analysis ----------

    static int season(int month) {
        if (month == 12 || month <= 2) return 0;  // Winter
        if (month <= 5) return 1;                 // Summer
        if (month <= 9) return 2;                 // SW monsoon
        return 3;                                 // NE monsoon
    }

    static double mean(double[] a) {
        double s = 0;
        for (double x : a) s += x;
        return s / a.length;
    }

    static double corr(double[] a, double[] b) {
        double ma = mean(a), mb = mean(b), sab = 0, saa = 0, sbb = 0;
        for (int i = 0; i < a.length; i++) {
            sab += (a[i] - ma) * (b[i] - mb);
            saa += (a[i] - ma) * (a[i] - ma);
            sbb += (b[i] - mb) * (b[i] - mb);
        }
        return sab / Math.sqrt(saa * sbb);
    }

    static void analyse() throws IOException {
        int n = dates.size();
        Set<Integer> yearSet = new TreeSet<>();
        for (LocalDate d : dates) yearSet.add(d.getYear());
        int years = yearSet.size();

        double[] monthTemp = new double[12], monthRain = new double[12];
        int[] monthCnt = new int[12];
        double[] seasTemp = new double[4], seasRain = new double[4];
        int[] seasCnt = new int[4];
        int rainyDays = 0, hotIdx = 0, wetIdx = 0;

        for (int i = 0; i < n; i++) {
            int m = dates.get(i).getMonthValue() - 1, s = season(m + 1);
            monthTemp[m] += v[2][i];
            monthRain[m] += v[3][i];
            monthCnt[m]++;
            seasTemp[s] += v[2][i];
            seasRain[s] += v[3][i];
            seasCnt[s]++;
            if (v[3][i] > 1) rainyDays++;
            if (v[0][i] > v[0][hotIdx]) hotIdx = i;
            if (v[3][i] > v[3][wetIdx]) wetIdx = i;
        }
        for (int m = 0; m < 12; m++) {
            monthTemp[m] /= monthCnt[m];
            monthRain[m] /= years; // average rainfall per year for that month
        }
        double[] seasAvgTemp = new double[4], seasAvgRain = new double[4];
        for (int s = 0; s < 4; s++) {
            seasAvgTemp[s] = seasCnt[s] == 0 ? 0 : seasTemp[s] / seasCnt[s];
            seasAvgRain[s] = seasRain[s] / years;
        }

        int hotM = 0, wetM = 0;
        for (int m = 1; m < 12; m++) {
            if (monthTemp[m] > monthTemp[hotM]) hotM = m;
            if (monthRain[m] > monthRain[wetM]) wetM = m;
        }

        // 30-day centred rolling average of mean temperature
        double[] roll = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 15), b = Math.min(n - 1, i + 14);
            double s = 0;
            for (int k = a; k <= b; k++) s += v[2][k];
            roll[i] = s / (b - a + 1);
        }

        // ----- summary.txt -----
        StringBuilder sum = new StringBuilder();
        sum.append(String.format(Locale.ROOT, "Weather Pattern Analysis - %s (%s to %s)%n", CITY, dates.get(0), dates.get(n - 1)));
        sum.append(String.format(Locale.ROOT, "Average temperature: %.1f C%n", mean(v[2])));
        sum.append(String.format(Locale.ROOT, "Hottest day: %s (%.1f C)%n", dates.get(hotIdx), v[0][hotIdx]));
        sum.append(String.format(Locale.ROOT, "Wettest day: %s (%.1f mm)%n", dates.get(wetIdx), v[3][wetIdx]));
        sum.append(String.format(Locale.ROOT, "Rainy days (>1 mm): %d of %d%n", rainyDays, n));
        sum.append("Hottest month on average: ").append(MONTHS[hotM]).append('\n');
        sum.append("Wettest month on average: ").append(MONTHS[wetM]).append('\n');
        sum.append(String.format(Locale.ROOT, "Rain vs mean-temperature correlation: %.2f%n%n", corr(v[3], v[2])));
        sum.append("Season-wise averages:\n");
        for (int s = 0; s < 4; s++)
            sum.append(String.format(Locale.ROOT, "  %-13s avg temp %.1f C | avg rainfall %.0f mm/year%n",
                    SEASONS[s], seasAvgTemp[s], seasAvgRain[s]));
        System.out.println("\n" + sum);
        Files.writeString(Path.of("output/summary.txt"), sum.toString());

        // ----- report.html -----
        StringBuilder labels = new StringBuilder("[");
        for (int i = 0; i < n; i++) labels.append(i > 0 ? "," : "").append('"').append(dates.get(i)).append('"');
        labels.append(']');

        StringBuilder table = new StringBuilder("<table><tr><th></th>");
        for (String c : COLS) table.append("<th>").append(c).append("</th>");
        table.append("</tr>");
        for (int r = 0; r < 5; r++) {
            table.append("<tr><th>").append(COLS[r]).append("</th>");
            for (int c = 0; c < 5; c++) {
                double x = corr(v[r], v[c]);
                int red = x > 0 ? 255 : (int) (255 + 120 * x);
                int blue = x < 0 ? 255 : (int) (255 - 120 * x);
                int green = (int) (255 - 100 * Math.abs(x));
                table.append(String.format(Locale.ROOT, "<td style=\"background:rgb(%d,%d,%d)\">%.2f</td>",
                        red, green, blue, x));
            }
            table.append("</tr>");
        }
        table.append("</table>");

        String html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>Weather Pattern Analysis - __CITY__</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
                <style>
                body{font-family:Segoe UI,Arial,sans-serif;max-width:1000px;margin:24px auto;padding:0 16px;color:#222}
                h1{margin-bottom:4px} .card{border:1px solid #ddd;border-radius:10px;padding:16px;margin:18px 0}
                pre{background:#f6f6f6;padding:12px;border-radius:8px;overflow:auto}
                table{border-collapse:collapse} td,th{border:1px solid #ccc;padding:6px 10px;text-align:center}
                </style></head><body>
                <h1>Weather Pattern Analysis - __CITY__</h1>
                <p>Data: Open-Meteo historical weather, __START__ to __END__</p>
                <div class="card"><h3>Summary</h3><pre>__SUMMARY__</pre></div>
                <div class="card"><h3>1. Daily mean temperature (with 30-day average)</h3><canvas id="c1"></canvas></div>
                <div class="card"><h3>2. Monthly rainfall and temperature</h3><canvas id="c2"></canvas></div>
                <div class="card"><h3>3. Average temperature by season</h3><canvas id="c3"></canvas></div>
                <div class="card"><h3>4. Correlation between variables</h3>__TABLE__</div>
                <script>
                new Chart(document.getElementById('c1'),{type:'line',data:{labels:__LABELS__,datasets:[
                 {label:'Daily mean (C)',data:__DAILY__,borderWidth:1,pointRadius:0,borderColor:'rgba(70,130,180,0.5)'},
                 {label:'30-day average',data:__ROLL__,borderWidth:2.5,pointRadius:0,borderColor:'crimson'}]},
                 options:{scales:{x:{ticks:{maxTicksLimit:12}}}}});
                new Chart(document.getElementById('c2'),{type:'bar',data:{labels:__MLABELS__,datasets:[
                 {label:'Avg rainfall per year (mm)',data:__MRAIN__,backgroundColor:'steelblue',yAxisID:'y'},
                 {type:'line',label:'Avg temperature (C)',data:__MTEMP__,borderColor:'crimson',yAxisID:'y1'}]},
                 options:{scales:{y:{title:{display:true,text:'mm'}},y1:{position:'right',grid:{drawOnChartArea:false},title:{display:true,text:'C'}}}}});
                new Chart(document.getElementById('c3'),{type:'bar',data:{labels:__SLABELS__,datasets:[
                 {label:'Avg temperature (C)',data:__STEMP__,backgroundColor:['#5b9bd5','#ed7d31','#70ad47','#7030a0']}]}});
                </script></body></html>
                """;
        html = html.replace("__CITY__", CITY).replace("__START__", START).replace("__END__", END)
                .replace("__SUMMARY__", sum.toString()).replace("__TABLE__", table.toString())
                .replace("__LABELS__", labels.toString()).replace("__DAILY__", arr(v[2]))
                .replace("__ROLL__", arr(roll))
                .replace("__MLABELS__", strArr(MONTHS)).replace("__MRAIN__", arr(monthRain))
                .replace("__MTEMP__", arr(monthTemp))
                .replace("__SLABELS__", strArr(SEASONS)).replace("__STEMP__", arr(seasAvgTemp));
        Files.writeString(Path.of("output/report.html"), html);
        System.out.println("Done. Open output/report.html in your browser to see the charts.");
    }

    static String arr(double[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++)
            sb.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "%.2f", a[i]));
        return sb.append(']').toString();
    }

    static String strArr(String[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append('"').append(a[i]).append('"');
        return sb.append(']').toString();
    }
}