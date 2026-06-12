import streamlit as st
import pandas as pd
import joblib
from pathlib import Path

# --- Configuration ---
st.set_page_config(page_title="IEX Energy Forecaster", layout="wide")
MODELS_DIR = Path("models")
MODEL_PATH = MODELS_DIR / "xgb_mcp_rs_mwh.joblib"
PROCESSED_DIR = Path("data/processed")

# --- UI Header ---
st.title("⚡ IEX Renewable Energy Forecaster")
st.markdown("### Real-time Market Clearing Price (MCP) Prediction Dashboard")

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
col1, col2 = st.columns([1, 1])

# 1. Predictions vs Actuals
X_test = test_df.drop(columns=["MCP (Rs/MWh)", "Final Scheduled Volume (MW)", "Date", "datetime"], errors='ignore')
predictions = model.predict(X_test.head(sample_size))

results = pd.DataFrame({
    "Actual Price": test_df["MCP (Rs/MWh)"].head(sample_size).values,
    "Predicted Price": predictions
})

with col1:
    st.subheader("Price Forecast Performance")
    st.line_chart(results)

# 2. Metrics
with col2:
    st.subheader("Model Metrics")
    mae = (results["Actual Price"] - results["Predicted Price"]).abs().mean()
    st.metric("Mean Absolute Error (Test Set)", f"{mae:.2f} Rs/MWh")
    st.info("The model is utilizing 46 optimized features to generate these forecasts.")

# 3. Raw Data Preview
st.subheader("Recent Input Features (Engineered)")
st.dataframe(X_test.head(sample_size))
