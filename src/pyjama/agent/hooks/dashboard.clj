(ns pyjama.agent.hooks.dashboard
  "Real-time web dashboard for monitoring Pyjama agents.
  
  Provides a web UI showing:
  - Active agents and their status
  - Real-time metrics and performance
  - Live log streaming
  - Hook status and configuration
  
  Start the dashboard with: (start-dashboard! 8080)"
  (:require [pyjama.agent.hooks :as hooks]
            [pyjama.agent.hooks.metrics :as metrics]
            [pyjama.agent.hooks.logging :as logging]
            [pyjama.agent.hooks.shared-metrics :as shared]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [java.net ServerSocket]
           [java.io BufferedReader InputStreamReader PrintWriter]
           [java.util.concurrent Executors]))

;; Dashboard state
(defonce ^:private dashboard-state
  (atom {:server nil
         :running false
         :port nil
         :agents {}  ;; {agent-id -> {:status :running :start-time ...}}
         :recent-logs []
         :max-logs 100}))

(defn- register-agent!
  "Register an agent as active."
  [agent-id]
  (swap! dashboard-state update :agents assoc agent-id
         {:status :running
          :start-time (System/currentTimeMillis)
          :last-seen (System/currentTimeMillis)}))

(defn- update-agent-activity!
  "Update agent's last-seen timestamp."
  [agent-id]
  (swap! dashboard-state update-in [:agents agent-id :last-seen]
         (fn [_] (System/currentTimeMillis))))

(defn- unregister-agent!
  "Mark agent as completed."
  [agent-id]
  (swap! dashboard-state update-in [:agents agent-id]
         assoc :status :completed :end-time (System/currentTimeMillis)))

(defn- add-log-entry!
  "Add a log entry to recent logs."
  [entry]
  (swap! dashboard-state update :recent-logs
         (fn [logs]
           (let [new-logs (conj logs entry)]
             (if (> (count new-logs) (:max-logs @dashboard-state))
               (vec (drop 1 new-logs))
               new-logs)))))

;; Hook to track agent activity
(defn agent-tracking-hook
  "Hook that tracks agent activity for the dashboard."
  [{:keys [tool-name ctx result]}]
  (when-let [agent-id (:id ctx)]
    (register-agent! agent-id)
    (update-agent-activity! agent-id)

    ;; Add to recent logs
    (add-log-entry! {:timestamp (System/currentTimeMillis)
                     :agent-id agent-id
                     :tool tool-name
                     :status (:status result)
                     :message (str "Agent " agent-id " executed " tool-name)})))

(defn- get-dashboard-data
  "Get current dashboard data from shared metrics file."
  []
  ;; Read from shared metrics file (cross-process)
  (shared/get-dashboard-data))

(defn- html-page
  "Generate the improved dashboard HTML page with collapsible workflows and navigation."
  []
  (str
   "<!DOCTYPE html>
<html lang=\"en\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>Pyjama Agent Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #333;
            padding: 20px;
            min-height: 100vh;
        }
        
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        
        header {
            background: white;
            padding: 30px;
            border-radius: 15px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.1);
            margin-bottom: 30px;
            text-align: center;
        }
        
        h1 {
            font-size: 2.5em;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            margin-bottom: 10px;
        }
        
        .subtitle {
            color: #666;
            font-size: 1.1em;
        }
        
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }
        
        .card {
            background: white;
            padding: 25px;
            border-radius: 15px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.1);
        }
        
        .card h2 {
            font-size: 1.5em;
            margin-bottom: 20px;
            color: #667eea;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .metric {
            display: flex;
            justify-content: space-between;
            padding: 12px 0;
            border-bottom: 1px solid #f0f0f0;
        }
        
        .metric:last-child {
            border-bottom: none;
        }
        
        .metric-label {
            color: #666;
            font-weight: 500;
        }
        
        .metric-value {
            font-weight: bold;
            color: #333;
            font-size: 1.1em;
        }
        
        .agent-item {
            padding: 15px;
            background: #f8f9fa;
            border-radius: 10px;
            margin-bottom: 10px;
            border-left: 4px solid #667eea;
        }
        
        .agent-item.completed {
            border-left-color: #28a745;
            opacity: 0.7;
        }
        
        .agent-name {
            font-weight: bold;
            color: #333;
            margin-bottom: 5px;
        }
        
        .agent-status {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 0.85em;
            font-weight: 600;
            text-transform: uppercase;
        }
        
        .status-running {
            background: #667eea;
            color: white;
        }
        
        .status-completed {
            background: #28a745;
            color: white;
        }
        
        .log-entry {
            padding: 10px;
            background: #f8f9fa;
            border-radius: 8px;
            margin-bottom: 8px;
            font-family: 'Monaco', 'Courier New', monospace;
            font-size: 0.9em;
            border-left: 3px solid #667eea;
        }
        
        .log-timestamp {
            color: #999;
            font-size: 0.85em;
        }
        
        .log-agent {
            color: #667eea;
            font-weight: bold;
        }
        
        .log-tool {
            color: #764ba2;
        }
        
        .status-ok {
            color: #28a745;
        }
        
        .status-error {
            color: #dc3545;
        }
        
        .refresh-info {
            text-align: center;
            color: white;
            margin-top: 20px;
            font-size: 0.9em;
        }
        
        .pulse {
            display: inline-block;
            width: 10px;
            height: 10px;
            background: #28a745;
            border-radius: 50%;
            margin-right: 8px;
            animation: pulse 2s infinite;
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        
        .empty-state {
            text-align: center;
            padding: 40px;
            color: #999;
        }
        
        .hook-badge {
            display: inline-block;
            padding: 6px 12px;
            background: #e9ecef;
            border-radius: 20px;
            margin: 5px;
            font-size: 0.9em;
        }
        
        .hook-count {
            background: #667eea;
            color: white;
            padding: 2px 8px;
            border-radius: 10px;
            margin-left: 5px;
            font-weight: bold;
        }
        
        /* Workflow Progress Styles */
        .workflow-steps {
            display: flex;
            align-items: center;
            margin: 20px 0;
            overflow-x: auto;
            padding: 10px 0;
        }
        
        .step-item {
            display: flex;
            align-items: center;
            min-width: 150px;
        }
        
        .step-circle {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
            font-size: 0.9em;
            flex-shrink: 0;
        }
        
        .step-circle.completed {
            background: #28a745;
            color: white;
        }
        
        .step-circle.running {
            background: #667eea;
            color: white;
            animation: pulse-step 1.5s infinite;
        }
        
        .step-circle.pending {
            background: #e9ecef;
            color: #999;
        }
        
        @keyframes pulse-step {
            0%, 100% { transform: scale(1); opacity: 1; }
            50% { transform: scale(1.1); opacity: 0.8; }
        }
        
        .step-connector {
            height: 3px;
            flex: 1;
            min-width: 30px;
            margin: 0 5px;
        }
        
        .step-connector.completed {
            background: #28a745;
        }
        
        .step-connector.pending {
            background: #e9ecef;
        }
        
        .step-label {
            margin-top: 8px;
            font-size: 0.85em;
            text-align: center;
            color: #666;
            word-wrap: break-word;
            max-width: 150px;
        }
        
        .step-wrapper {
            display: flex;
            flex-direction: column;
            align-items: center;
        }
    </style>
</head>
<body>
    <div class=\"container\">
        <header>
            <h1>🎣 Pyjama Agent Dashboard</h1>
            <p class=\"subtitle\">Real-time monitoring of agent execution, metrics, and hooks</p>
        </header>
        
        <div class=\"grid\">
            <div class=\"card\">
                <h2>📊 Global Metrics</h2>
                <div id=\"global-metrics\">
                    <div class=\"empty-state\">Loading metrics...</div>
                </div>
            </div>
            
            <div class=\"card\">
                <h2>🤖 Active Agents</h2>
                <div id=\"active-agents\">
                    <div class=\"empty-state\">No agents running</div>
                </div>
            </div>
            
            <div class=\"card\">
                <h2>🔧 Registered Hooks</h2>
                <div id=\"hooks-status\">
                    <div class=\"empty-state\">Loading hooks...</div>
                </div>
            </div>
        </div>
        
        <div class=\"card\">
            <h2>📝 Recent Activity</h2>
            <div id=\"recent-logs\">
                <div class=\"empty-state\">No recent activity</div>
            </div>
        </div>
        
        <div class=\"refresh-info\">
            <span class=\"pulse\"></span>
            Auto-refreshing every 2 seconds
        </div>
    </div>
    
    <script>
        function formatTimestamp(ts) {
            const date = new Date(ts);
            return date.toLocaleTimeString();
        }
        
        function formatDuration(ms) {
            if (ms < 1000) return ms.toFixed(2) + 'ms';
            return (ms / 1000).toFixed(2) + 's';
        }
        
        function updateDashboard() {
            fetch('/api/data')
                .then(res => res.json())
                .then(data => {
                    // Update global metrics
                    const metrics = data.metrics.global;
                    document.getElementById('global-metrics').innerHTML = `
                        <div class=\"metric\">
                            <span class=\"metric-label\">Total Executions</span>
                            <span class=\"metric-value\">${metrics.count || 0}</span>
                        </div>
                        <div class=\"metric\">
                            <span class=\"metric-label\">Success Rate</span>
                            <span class=\"metric-value\">${((metrics['success-rate'] || 0) * 100).toFixed(1)}%</span>
                        </div>
                        <div class=\"metric\">
                            <span class=\"metric-label\">Avg Duration</span>
                            <span class=\"metric-value\">${formatDuration(metrics['avg-duration-ms'] || 0)}</span>
                        </div>
                        <div class=\"metric\">
                            <span class=\"metric-label\">Throughput</span>
                            <span class=\"metric-value\">${(metrics.throughput || 0).toFixed(2)} ops/sec</span>
                        </div>
                    `;
                    
                    // Update active agents
                    const agents = data.agents;
                    const agentKeys = Object.keys(agents);
                    if (agentKeys.length === 0) {
                        document.getElementById('active-agents').innerHTML = '<div class=\"empty-state\">No agents running</div>';
                    } else {
                        document.getElementById('active-agents').innerHTML = agentKeys.map(agentId => {
                            const agent = agents[agentId];
                            const status = agent.status || 'unknown';
                            const statusClass = status === 'running' ? 'status-running' : 'status-completed';
                            const itemClass = status === 'running' ? 'agent-item' : 'agent-item completed';
                            
                            // Calculate duration with fallbacks
                            let duration = 0;
                            if (agent['end-time'] && agent['start-time']) {
                                duration = agent['end-time'] - agent['start-time'];
                            } else if (agent['start-time']) {
                                duration = Date.now() - agent['start-time'];
                            } else if (agent['last-seen']) {
                                // Fallback: estimate from last-seen
                                duration = Date.now() - agent['last-seen'];
                            }
                            
                            // Build workflow progress if available
                            let workflowHTML = '';
                            if (agent.steps && agent.steps.length > 0) {
                                const currentStep = agent['current-step'];
                                const steps = agent.steps;
                                
                                workflowHTML = '<div class=\"workflow-steps\" style=\"margin-top: 12px;\">';
                                steps.forEach((step, index) => {
                                    const isCompleted = step.status === 'ok' || step.status === 'completed';
                                    const isRunning = step.status === 'running' || step['step-id'] === currentStep;
                                    
                                    let circleClass = 'pending';
                                    let circleContent = index + 1;
                                    if (isCompleted) {
                                        circleClass = 'completed';
                                        circleContent = '✓';
                                    } else if (isRunning) {
                                        circleClass = 'running';
                                        circleContent = '▶';
                                    }
                                    
                                    workflowHTML += '<div class=\"step-item\"><div class=\"step-wrapper\"><div class=\"step-circle ' + circleClass + '\">' + circleContent + '</div><div class=\"step-label\">' + step['step-id'] + '</div></div>';
                                    
                                    if (index < steps.length - 1) {
                                        const connectorClass = isCompleted ? 'completed' : 'pending';
                                        workflowHTML += '<div class=\"step-connector ' + connectorClass + '\"></div>';
                                    }
                                    
                                    workflowHTML += '</div>';
                                });
                                workflowHTML += '</div>';
                            }
                            
                            return '<div class=\"' + itemClass + '\"><div class=\"agent-name\">' + agentId + '</div><span class=\"agent-status ' + statusClass + '\">' + status + '</span><div style=\"margin-top: 8px; color: #666; font-size: 0.9em;\">Duration: ' + formatDuration(duration) + '</div>' + workflowHTML + '</div>';
                        }).join('');
                    }
                    
                    // Update hooks status
                    const hooks = data.hooks.registered;
                    const hookKeys = Object.keys(hooks);
                    if (hookKeys.length === 0) {
                        document.getElementById('hooks-status').innerHTML = '<div class=\"empty-state\">No hooks registered</div>';
                    } else {
                        document.getElementById('hooks-status').innerHTML = hookKeys.map(tool => {
                            const count = hooks[tool];
                            return '<span class=\"hook-badge\">' + tool + '<span class=\"hook-count\">' + count + '</span></span>';
                        }).join('');
                    }
                    
                    // Workflow progress is now shown inline with each agent
                    
                    // Update recent logs
                    const logs = data['recent-logs'];
                    if (logs.length === 0) {
                        document.getElementById('recent-logs').innerHTML = '<div class=\"empty-state\">No recent activity</div>';
                    } else {
                        document.getElementById('recent-logs').innerHTML = logs.slice().reverse().slice(0, 20).map(log => {
                            const statusClass = log.status === 'ok' ? 'status-ok' : 'status-error';
                            return '<div class=\"log-entry\"><span class=\"log-timestamp\">' + formatTimestamp(log.timestamp) + '</span><span class=\"log-agent\">' + log['agent-id'] + '</span> executed <span class=\"log-tool\">' + log.tool + '</span> - <span class=\"' + statusClass + '\">' + (log.status || 'unknown') + '</span></div>';
                        }).join('');
                    }
                })
                .catch(err => console.error('Failed to fetch dashboard data:', err));
        }
        
        // Initial update
        updateDashboard();
        
        // Auto-refresh every 2 seconds
        setInterval(updateDashboard, 2000);
    </script>
</body>
</html>"))

(defn- handle-request
  "Handle HTTP request."
  [request-line]
  (cond
    (str/starts-with? request-line "GET / ")
    {:status 200
     :content-type "text/html"
     :headers {"Cache-Control" "no-cache, no-store, must-revalidate"
               "Pragma" "no-cache"
               "Expires" "0"}
     :body (html-page)}

    (str/starts-with? request-line "GET /api/data ")
    {:status 200
     :content-type "application/json"
     :headers {"Cache-Control" "no-cache, no-store, must-revalidate"
               "Pragma" "no-cache"
               "Expires" "0"}
     :body (json/write-str (get-dashboard-data))}

    :else
    {:status 404
     :content-type "text/plain"
     :body "Not Found"}))

(defn- handle-client
  "Handle a client connection."
  [client-socket]
  (try
    (with-open [in (BufferedReader. (InputStreamReader. (.getInputStream client-socket)))
                out (PrintWriter. (.getOutputStream client-socket) true)]
      (let [request-line (.readLine in)
            response (handle-request request-line)]
        (.println out (str "HTTP/1.1 " (:status response) " OK"))
        (.println out (str "Content-Type: " (:content-type response)))
        (.println out "Connection: close")
        ;; Write custom headers if present
        (doseq [[k v] (:headers response)]
          (.println out (str k ": " v)))
        (.println out "")
        (.println out (:body response))))
    (catch Exception e
      (println "Error handling client:" (.getMessage e)))
    (finally
      (.close client-socket))))

(defn start-dashboard!
  "Start the dashboard web server.
  
  Args:
    port - Port number to listen on (default: 8090)
  
  Returns: Server info map
  
  Example:
    (start-dashboard! 8090)
    ;; Open http://localhost:8090 in your browser"
  ([]
   (start-dashboard! 8090))
  ([port]
   (if (:running @dashboard-state)
     (do
       (println "⚠️  Dashboard already running on port" (:port @dashboard-state))
       @dashboard-state)

     (try
       (let [server-socket (ServerSocket. port)
             executor (Executors/newFixedThreadPool 10)]

         ;; Register dashboard tracking hook
         (hooks/register-hook! :write-file agent-tracking-hook)
         (hooks/register-hook! :read-files agent-tracking-hook)
         (hooks/register-hook! :list-directory agent-tracking-hook)
         (hooks/register-hook! :cat-files agent-tracking-hook)
         (hooks/register-hook! :discover-codebase agent-tracking-hook)

         ;; Start server thread
         (.execute executor
                   (fn []
                     (println (str "🚀 Dashboard server started on http://localhost:" port))
                     (println "   Open in your browser to view real-time agent monitoring")
                     (while (:running @dashboard-state)
                       (try
                         (let [client (.accept server-socket)]
                           (.execute executor #(handle-client client)))
                         (catch Exception e
                           (when (:running @dashboard-state)
                             (println "Server error:" (.getMessage e))))))))

         (swap! dashboard-state assoc
                :server server-socket
                :running true
                :port port)

         {:status :ok
          :port port
          :url (str "http://localhost:" port)
          :message (str "Dashboard running on http://localhost:" port)})

       (catch Exception e
         {:status :error
          :error (.getMessage e)})))))

(defn stop-dashboard!
  "Stop the dashboard web server."
  []
  (when-let [server (:server @dashboard-state)]
    (swap! dashboard-state assoc :running false)
    (.close server)

    ;; Unregister tracking hooks
    (hooks/unregister-hook! :write-file agent-tracking-hook)
    (hooks/unregister-hook! :read-files agent-tracking-hook)
    (hooks/unregister-hook! :list-directory agent-tracking-hook)
    (hooks/unregister-hook! :cat-files agent-tracking-hook)
    (hooks/unregister-hook! :discover-codebase agent-tracking-hook)

    (println "🛑 Dashboard server stopped")
    {:status :ok :message "Dashboard stopped"}))

(comment
  ;; Start the dashboard
  (start-dashboard! 8080)

  ;; Open http://localhost:8080 in your browser

  ;; Stop the dashboard
  (stop-dashboard!))
