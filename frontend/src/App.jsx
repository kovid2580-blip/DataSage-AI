import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, BarChart3, Database, FileUp, History, Loader2, Sparkles } from 'lucide-react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { askQuestion, fetchData, fetchHistory, uploadCsv } from './api';

const chartColors = ['#176B87', '#64CCC5', '#E86A5B', '#F2BE22', '#5C5470', '#2E8A57'];

export default function App() {
  const [preview, setPreview] = useState(null);
  const [history, setHistory] = useState([]);
  const [question, setQuestion] = useState('');
  const [chartResult, setChartResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    loadInitialData();
  }, []);

  const chartData = useMemo(() => {
    if (!chartResult) {
      return [];
    }
    return chartResult.labels.map((label, index) => ({
      label,
      value: Number(chartResult.values[index] || 0),
    }));
  }, [chartResult]);

  const loadInitialData = async () => {
    try {
      const [dataResponse, historyResponse] = await Promise.allSettled([fetchData(), fetchHistory()]);
      if (dataResponse.status === 'fulfilled') {
        setPreview(dataResponse.value.data);
      }
      if (historyResponse.status === 'fulfilled') {
        setHistory(historyResponse.value.data);
      }
    } catch {
      setPreview(null);
    }
  };

  const handleUpload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    setUploading(true);
    setError('');
    try {
      const response = await uploadCsv(file);
      setPreview(response.data);
      setChartResult(null);
      await refreshHistory();
    } catch (apiError) {
      setError(readError(apiError, 'Could not upload CSV file.'));
    } finally {
      setUploading(false);
      event.target.value = '';
    }
  };

  const handleAsk = async (event) => {
    event.preventDefault();
    if (!question.trim()) {
      setError('Enter a question about your CSV data.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const response = await askQuestion(question.trim());
      setChartResult(response.data);
      setQuestion('');
      await refreshHistory();
    } catch (apiError) {
      setError(readError(apiError, 'Could not answer this question.'));
    } finally {
      setLoading(false);
    }
  };

  const refreshHistory = async () => {
    const response = await fetchHistory();
    setHistory(response.data);
  };

  return (
    <main className="min-h-screen">
      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-ocean">AI Data Analyst Dashboard</p>
            <h1 className="mt-2 text-3xl font-bold text-ink sm:text-4xl">Ask questions from your CSV data</h1>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <Stat icon={Database} label="Rows" value={preview?.totalRows ?? 0} />
            <Stat icon={BarChart3} label="Columns" value={preview?.headers?.length ?? 0} />
            <Stat icon={History} label="Queries" value={history.length} />
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-7xl gap-5 px-4 py-6 sm:px-6 lg:grid-cols-[360px_1fr] lg:px-8">
        <aside className="space-y-5">
          <UploadCard uploading={uploading} onUpload={handleUpload} fileName={preview?.fileName} />
          <QuestionCard
            question={question}
            loading={loading}
            disabled={!preview}
            onQuestionChange={setQuestion}
            onSubmit={handleAsk}
          />
          {error && <ErrorMessage message={error} />}
          {chartResult?.summary && <SummaryCard summary={chartResult.summary} />}
          <HistorySection history={history} />
        </aside>

        <div className="space-y-5">
          <ChartPanel chartResult={chartResult} chartData={chartData} />
          <DataPreview preview={preview} />
        </div>
      </section>
    </main>
  );
}

function Stat({ icon: Icon, label, value }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <div className="mt-1 text-2xl font-semibold text-ink">{value}</div>
    </div>
  );
}

function UploadCard({ uploading, onUpload, fileName }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-soft">
      <div className="mb-4 flex items-center gap-3">
        <div className="rounded-lg bg-ocean/10 p-2 text-ocean">
          <FileUp className="h-5 w-5" />
        </div>
        <h2 className="text-lg font-semibold">CSV Upload</h2>
      </div>
      <label className="flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center transition hover:border-ocean hover:bg-ocean/5">
        {uploading ? <Loader2 className="h-8 w-8 animate-spin text-ocean" /> : <FileUp className="h-8 w-8 text-ocean" />}
        <span className="mt-3 text-sm font-medium text-ink">{uploading ? 'Uploading...' : 'Choose a CSV file'}</span>
        <input className="sr-only" type="file" accept=".csv,.txt,text/csv,text/plain" onChange={onUpload} disabled={uploading} />
      </label>
      {fileName && <p className="mt-3 truncate text-sm text-slate-500">Loaded: {fileName}</p>}
    </section>
  );
}

function QuestionCard({ question, loading, disabled, onQuestionChange, onSubmit }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-soft">
      <div className="mb-4 flex items-center gap-3">
        <div className="rounded-lg bg-mint/20 p-2 text-ocean">
          <Sparkles className="h-5 w-5" />
        </div>
        <h2 className="text-lg font-semibold">Ask AI</h2>
      </div>
      <form className="space-y-3" onSubmit={onSubmit}>
        <input
          className="w-full rounded-lg border border-slate-300 px-4 py-3 outline-none transition focus:border-ocean focus:ring-2 focus:ring-ocean/20 disabled:bg-slate-100"
          value={question}
          onChange={(event) => onQuestionChange(event.target.value)}
          placeholder="show sales by month"
          disabled={disabled || loading}
        />
        <button
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-ocean px-4 py-3 font-semibold text-white transition hover:bg-ink disabled:cursor-not-allowed disabled:bg-slate-300"
          type="submit"
          disabled={disabled || loading}
        >
          {loading ? <Loader2 className="h-5 w-5 animate-spin" /> : <Sparkles className="h-5 w-5" />}
          Analyze
        </button>
      </form>
    </section>
  );
}

function ChartPanel({ chartResult, chartData }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-soft">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">Chart</h2>
        {chartResult && <span className="rounded-full bg-mint/20 px-3 py-1 text-sm font-medium text-ocean">{chartResult.chartType}</span>}
      </div>
      <div className="h-[360px]">
        {chartData.length ? (
          <ResponsiveContainer width="100%" height="100%">
            {renderChart(chartResult.chartType, chartData)}
          </ResponsiveContainer>
        ) : (
          <div className="flex h-full items-center justify-center rounded-lg bg-slate-50 text-center text-slate-500">
            Upload a CSV and ask a question to generate a chart.
          </div>
        )}
      </div>
    </section>
  );
}

function renderChart(chartType, data) {
  if (chartType === 'pie') {
    return (
      <PieChart>
        <Tooltip />
        <Pie data={data} dataKey="value" nameKey="label" outerRadius={125} label>
          {data.map((entry, index) => (
            <Cell key={entry.label} fill={chartColors[index % chartColors.length]} />
          ))}
        </Pie>
      </PieChart>
    );
  }

  if (chartType === 'line') {
    return (
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="label" />
        <YAxis />
        <Tooltip />
        <Line type="monotone" dataKey="value" stroke="#176B87" strokeWidth={3} />
      </LineChart>
    );
  }

  if (chartType === 'area') {
    return (
      <AreaChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="label" />
        <YAxis />
        <Tooltip />
        <Area type="monotone" dataKey="value" stroke="#176B87" fill="#64CCC5" fillOpacity={0.45} />
      </AreaChart>
    );
  }

  return (
    <BarChart data={data}>
      <CartesianGrid strokeDasharray="3 3" />
      <XAxis dataKey="label" />
      <YAxis />
      <Tooltip />
      <Bar dataKey="value" fill="#176B87" radius={[6, 6, 0, 0]} />
    </BarChart>
  );
}

function DataPreview({ preview }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-soft">
      <div className="mb-4 flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <h2 className="text-lg font-semibold">Data Preview</h2>
        {preview && <p className="text-sm text-slate-500">Showing {preview.rows.length} of {preview.totalRows} rows</p>}
      </div>
      {!preview ? (
        <div className="rounded-lg bg-slate-50 px-4 py-10 text-center text-slate-500">No CSV data loaded yet.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200">
                {preview.headers.map((header) => (
                  <th key={header} className="whitespace-nowrap px-3 py-3 font-semibold text-slate-700">{header}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {preview.rows.map((row, rowIndex) => (
                <tr key={`${rowIndex}-${JSON.stringify(row)}`} className="border-b border-slate-100 last:border-0">
                  {preview.headers.map((header) => (
                    <td key={header} className="whitespace-nowrap px-3 py-3 text-slate-600">{row[header]}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function SummaryCard({ summary }) {
  return (
    <section className="rounded-lg border border-mint/40 bg-white p-5 shadow-soft">
      <h2 className="mb-2 text-lg font-semibold">AI Summary</h2>
      <p className="text-sm leading-6 text-slate-600">{summary}</p>
    </section>
  );
}

function HistorySection({ history }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-soft">
      <h2 className="mb-4 text-lg font-semibold">Query History</h2>
      {history.length === 0 ? (
        <p className="text-sm text-slate-500">No questions asked yet.</p>
      ) : (
        <div className="space-y-3">
          {history.map((item) => (
            <article key={item.id} className="rounded-lg border border-slate-100 bg-slate-50 p-3">
              <p className="text-sm font-medium text-ink">{item.question}</p>
              <p className="mt-1 text-xs text-slate-500">{new Date(item.createdAt).toLocaleString()}</p>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function ErrorMessage({ message }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-coral/30 bg-coral/10 p-4 text-sm text-coral">
      <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
      <span>{message}</span>
    </div>
  );
}

function readError(apiError, fallback) {
  return apiError?.response?.data?.message || fallback;
}
