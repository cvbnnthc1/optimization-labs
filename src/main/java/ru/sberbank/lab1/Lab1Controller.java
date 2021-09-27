package ru.sberbank.lab1;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.emptyList;

@RestController
@RequestMapping("/lab1")
public class Lab1Controller {

    private static final String URL = "http://export.rbc.ru/free/selt.0/free.fcgi?period=DAILY&tickers=USD000000TOD&separator=TAB&data_format=BROWSER";
    private static final long oneDayInSec = 24 * 60 * 60L;
    private static final String API_URL = "https://api.darksky.net/forecast/7ba6164198e89cb2e6b2454d90e7b41d/";
    private static final String COORDINATES = "34.053044,-118.243750,";
    private static final String API_ARG = "?exclude=daily";
    private static final HashMap<Long, Double> weatherCache = new HashMap<>();

    @GetMapping("/quotes")
    public List<Quote> quotes(@RequestParam("days") int days) throws ExecutionException, InterruptedException, ParseException {
        AsyncHttpClient client = AsyncHttpClientFactory.create(new AsyncHttpClientFactory.AsyncHttpClientConfig());
        Response response = client.prepareGet(URL + "&lastdays=" + days).execute().get();

        String body = response.getResponseBody();
        String[] lines = body.split("\n");

        List<Quote> quotes = new ArrayList<>();

        Map<String, Double> maxMap = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String[] line = lines[i].split("\t");
            Date date = new SimpleDateFormat("yyyy-MM-dd").parse(line[1]);
            String year = line[1].split("-")[0];
            String month = line[1].split("-")[1];
            String monthYear = year + month;
            Double high = Double.parseDouble(line[3]);

            Double maxYear = maxMap.get(year);
            if (maxYear == null || maxYear < high) {
                maxMap.put(year, high);
                if (maxYear != null) {
                    List<Quote> newQuotes = new ArrayList<>();
                    for (Quote oldQuote : quotes) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(oldQuote.getDate());
                        int oldYear = cal.get(Calendar.YEAR);
                        if (oldYear == Integer.parseInt(year)) {
                            if (oldQuote.getMaxInYear() < high) {
                                Quote newQuote = oldQuote.setMaxInYear(high);
                                newQuotes.add(newQuote);
                            } else {
                                newQuotes.add(oldQuote);
                            }
                        }
                    }
                    quotes.clear();
                    quotes.addAll(newQuotes);
                }
            }

            Double maxMonth = maxMap.get(monthYear);
            if (maxMonth == null || maxMonth < high) {
                maxMap.put(monthYear, high);
                if (maxMonth != null) {
                    List<Quote> newQuotes = new ArrayList<>();
                    for (Quote oldQuote : quotes) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(oldQuote.getDate());
                        int oldYear = cal.get(Calendar.YEAR);
                        int oldMonth = cal.get(Calendar.MONTH);
                        if (oldYear == Integer.parseInt(year) && oldMonth == Integer.parseInt(month)) {
                            if (oldQuote.getMaxInMonth() < high) {
                                Quote newQuote = oldQuote.setMaxInMonth(high);
                                quotes.remove(oldQuote);
                                quotes.add(newQuote);
                            }
                        }
                    }
                }
            }

            Quote quote = new Quote(line[0],
                    new SimpleDateFormat("yyyy-MM-dd").parse(line[1]),
                    Double.parseDouble(line[2]),
                    Double.parseDouble(line[3]),
                    Double.parseDouble(line[4]),
                    Double.parseDouble(line[5]),
                    Long.parseLong(line[6]),
                    Double.parseDouble(line[7]));
            quote = quote.setMaxInMonth(maxMap.get(monthYear));
            quote = quote.setMaxInYear(maxMap.get(year));

            quotes.add(quote);
        }
        return quotes;
    }

    @GetMapping("/weather")
    public List<Double> getWeatherForPeriod(Integer days) {
        try {
            return getTemperatureForLastDays(days);
        } catch (JSONException e) {
        }

        return emptyList();
    }

    //Добавим кэш, в котором хранятся температуры для дат, для которых уже были вызовы
    //заменил Long на long для экономии памяти
    //Сделал oneDayInSec константой, чтобы каждый раз ее не пересчитывать
    //Вынес объявление currentDayInSec из цикла
    public List<Double> getTemperatureForLastDays(int days) throws JSONException {
        List<Double> temps = new ArrayList<>();
        long currentDayInSec = Calendar.getInstance().getTimeInMillis() / 1000;
        for (int i = 0; i < days; i++) {
            long curDateSec = currentDayInSec - i * oneDayInSec;
            long curDay = curDateSec / oneDayInSec;
            Double cachedValue = weatherCache.getOrDefault(curDay, null);
            if (cachedValue == null) {
                double curTemp = getTemperatureFromInfo(Long.toString(curDateSec));
                weatherCache.putIfAbsent(curDay, curTemp);
                temps.add(curTemp);
            } else {
                temps.add(cachedValue);
            }
        }

        return temps;
    }

    //Заменим конкатенацию строк на StringBuilder
    //Вынесем повторяющиеся строки в константы
    public String getTodayWeather(String date) {
        StringBuilder request = new StringBuilder();
        request.append(API_URL)
                .append(COORDINATES)
                .append(date)
                .append(API_ARG);

        RestTemplate restTemplate = new RestTemplate();
        String fooResourceUrl = request.toString();
        System.out.println(fooResourceUrl);
        ResponseEntity<String> response = restTemplate.getForEntity(fooResourceUrl, String.class);
        String info = response.getBody();
        System.out.println(info);
        return info;
    }

    //Не создаем промежуточную переменную, сразу возращаем результат
    //Поменял возвращаемый тип на double
    public double getTemperatureFromInfo(String date) throws JSONException {
        String info = getTodayWeather(date);
        return getTemperature(info);
    }

    //Не создаем промежуточную переменную, сразу возращаем результат
    //Поменял возвращаемый тип на double
    //Убал лишние преобразования из String в JSonObject и обратно
    public double getTemperature(String info) throws JSONException {
        return new JSONObject(info)
                    .getJSONObject("hourly")
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getDouble("temperature");
    }
}

