# Weather Pattern Analysis - Chennai

## Objective
Analyse historical weather data for Chennai (2023-2025) to understand temperature trends, rainfall patterns, seasonal behaviour and the relationships between weather variables.

## Data Source
[Open-Meteo Historical Weather API](https://open-meteo.com/) (free, no API key). Daily values used:
- Maximum, minimum and mean temperature (°C)
- Precipitation (mm)
- Maximum wind speed (km/h)

## Tools Used
- Java 17+ (no external libraries; `HttpClient` for the API call)
- Chart.js (charts in the HTML report)
- VS Code

## Method
1. **Data collection:** daily data for 2023-01-01 to 2025-12-31 downloaded from the Open-Meteo API.
2. **Data cleaning:** missing values filled using the nearest valid value; cleaned data saved to `output/cleaned_weather_data.csv`.
3. **Analysis:**
   - 30-day rolling average of temperature to show the trend
   - Monthly average temperature and rainfall
   - Season-wise comparison (Winter, Summer, South-West Monsoon, North-East Monsoon)
   - Pearson correlation between temperature, rainfall and wind speed
4. **Visualization:** charts generated in `output/report.html`.

## Visualizations

### 1. Daily Mean Temperature with 30-day Average
![Temperature Trend](screenshots/chart1.png)

### 2. Monthly Rainfall and Temperature
![Monthly Rainfall and Temperature](screenshots/chart2.png)

### 3. Average Temperature by Season
![Seasonal Temperature](screenshots/chart3.png)

### 4. Correlation Between Variables
![Correlation Table](screenshots/chart4.png)

## Key Findings
Full numbers are in `output/summary.txt`.

- **Hottest month (on average):** <fill from summary.txt>
- **Wettest month (on average):** <fill from summary.txt>
- **Rainy days (>1 mm):** <fill from summary.txt>
- **Rain vs temperature correlation:** <fill from summary.txt>
- **Seasonal pattern:** <fill from summary.txt - which season is hottest and which gets the most rain>

## Project Structure
```
WeatherPatternAnalysis/
├── WeatherPatternAnalysis.java
├── output/
│   ├── cleaned_weather_data.csv
│   ├── summary.txt
│   └── report.html
└── README.md
```

## How to Run
```
javac WeatherPatternAnalysis.java
java WeatherPatternAnalysis
```
Then open `output/report.html` in a browser (internet needed to load Chart.js).

## Conclusion
The analysis shows clear seasonal patterns in Chennai's weather, with temperature and rainfall varying strongly across months and monsoon seasons.
