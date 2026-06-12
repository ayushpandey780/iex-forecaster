import streamlit as st
import pandas as pd
import joblib
from pathlib import Path
import subprocess
import os
import urllib.request
import ssl

# --- Java Dependency Setup ---
GSON_JAR = "gson-2.10.1.jar"
if not os.path.exists(GSON_JAR):
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen("https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar", context=ctx) as response, open(GSON_JAR, 'wb') as out_file:
        out_file.write(response.read())

# Always force recompile to ensure the latest Java logic is used
try:
    subprocess.run(["javac", "-cp", GSON_JAR, "GeminiInterpreter.java"], check=True)
except subprocess.CalledProcessError as e:
    st.error("Failed to compile Java microservice.")

# --- Configuration ---
st.set_page_config(page_title="IEX Energy Forecaster", layout="wide")
MODELS_DIR = Path("models")
MODEL_PATH = MODELS_DIR / "xgb_mcp_rs_mwh.joblib"
PROCESSED_DIR = Path("data/processed")

# --- Load Data & Model ---
@st.cache_resource
def load_model():
    return joblib.load(MODEL_PATH)

@st.cache_data
def load_test_data():
    return pd.read_parquet(PROCESSED_DIR / "test.parquet")

model = load_model()
test_df = load_test_data()

# --- Sidebar Controls ---
st.sidebar.header("Dashboard Controls")
sample_size = st.sidebar.slider("Number of Blocks to Predict", 5, 50, 10)

# --- Main Dashboard ---
st.title("⚡ IEX Renewable Energy Forecaster")
st.markdown("### Real-time Market Clearing Price (MCP) Prediction Dashboard")

col1, col2 = st.columns([1, 1])

X_test = test_df.drop(columns=["MCP (Rs/MWh)", "Final Scheduled Volume (MW)", "Date", "datetime"], errors='ignore')
predictions = model.predict(X_test.head(sample_size))

results = pd.DataFrame({
    "Actual Price": test_df["MCP (Rs/MWh)"].head(sample_size).values,
    "Predicted Price": predictions
})

with col1:
    st.subheader("Price Forecast Performance")
    st.line_chart(results)

with col2:
    st.subheader("Model Metrics")
    mae = (results["Actual Price"] - results["Predicted Price"]).abs().mean()
    st.metric("Mean Absolute Error (Test Set)", f"{mae:.2f} Rs/MWh")
    st.info("The model is utilizing 46 optimized features to generate these forecasts.")

    # --- Gemini XAI Integration ---
    st.subheader("Automated Market Analysis & XAI")
    pred_string = str(predictions.tolist())
    
    # 1. Base Summary
    if st.button("Generate Summary"):
        with st.spinner("Analyzing price momentum..."):
            try:
                result = subprocess.run(
                    ["java", "-cp", f".:{GSON_JAR}", "GeminiInterpreter", pred_string], 
                    capture_output=True, text=True, check=True
                )
                st.success(result.stdout)
            except subprocess.CalledProcessError as e:
                st.error(f"Execution failed: {e.stderr}")

    st.markdown("---")
    
    # 2. Interactive XAI Explainer Input
    st.markdown("##### 🔍 Ask the Explainable AI (XAI):")
    user_query = st.text_input("e.g., 'What algorithm does this use?', 'Why 46 features?'")
    
    if user_query:
        with st.spinner("Consulting XAI Engine..."):
            try:
                # Pass both predictions and user query
                result = subprocess.run(
                    ["java", "-cp", f".:{GSON_JAR}", "GeminiInterpreter", pred_string, user_query], 
                    capture_output=True, text=True, check=True
                )
                st.info(f"**XAI Explanation:** {result.stdout}")
            except subprocess.CalledProcessError as e:
                st.error(f"XAI Execution failed: {e.stderr}")

st.subheader("Recent Input Features (Engineered)")
st.dataframe(X_test.head(sample_size))
