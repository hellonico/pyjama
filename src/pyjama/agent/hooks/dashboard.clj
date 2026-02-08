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
            [pyjama.agent.visualize :as visualize]
            [pyjama.core :as pyjama]
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
  "Generate the improved dashboard HTML page with tabbed navigation (ES5 compatible)."
  []
  (str
   "<!DOCTYPE html>
<html lang=\"en\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>Pyjama Agent Dashboard</title>
    <script src=\"https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js\"></script>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
        }
        
        header {
            background: white;
            padding: 20px 30px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        h1 {
            font-size: 2em;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        
        .subtitle { color: #666; font-size: 0.9em; margin-top: 5px; }
        
        .nav {
            background: white;
            padding: 15px 30px;
            display: flex;
            gap: 20px;
            border-bottom: 2px solid #f0f0f0;
        }
        
        .nav-item {
            padding: 10px 20px;
            cursor: pointer;
            border-radius: 8px;
            font-weight: 500;
            color: #666;
            transition: 0.2s;
        }
        
        .nav-item:hover { background: #f8f9fa; }
        .nav-item.active {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        
        .container { max-width: 1400px; margin: 0 auto; padding: 30px 20px; }
        .view { display: none; }
        .view.active { display: block; }
        
        .card {
            background: white;
            padding: 25px;
            border-radius: 15px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.1);
            margin-bottom: 20px;
        }
        
        .card h2 {
            font-size: 1.5em;
            margin-bottom: 20px;
            color: #667eea;
        }
        
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
        }
        
        .metric {
            padding: 15px;
            background: #f8f9fa;
            border-radius: 10px;
        }
        
        .metric-label { color: #666; font-size: 0.9em; }
        .metric-value { font-weight: bold; color: #333; font-size: 1.8em; }
        
        .agent-card {
            padding: 20px;
            background: #f8f9fa;
            border-radius: 12px;
            margin-bottom: 15px;
            border-left: 4px solid #667eea;
            cursor: pointer;
            transition: 0.2s;
        }
        
        .agent-card:hover {
            background: #e9ecef;
            transform: translateX(5px);
        }
        
        .agent-card.completed { border-left-color: #28a745; opacity: 0.8; }
        
        .agent-header {
            display: flex;
            justify-content: space-between;
            margin-bottom: 10px;
        }
        
        .agent-name { font-weight: bold; font-size: 1.1em; }
        
        .agent-status {
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 0.8em;
            font-weight: 600;
            text-transform: uppercase;
        }
        
        .agent-status.running { background: #667eea; color: white; }
        .agent-status.completed { background: #28a745; color: white; }
        
        .agent-meta { color: #666; font-size: 0.9em; margin-bottom: 15px; }
        
        .current-step-preview {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 12px;
            background: white;
            border-radius: 8px;
        }
        
        .step-indicator {
            width: 30px;
            height: 30px;
            border-radius: 50%;
            background: #667eea;
            color: white;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
        }
        
        .step-indicator.completed { background: #28a745; }
        .step-info { flex: 1; }
        .step-name { font-weight: 600; }
        .step-count { color: #666; font-size: 0.85em; margin-top: 3px; }
        .expand-hint { color: #667eea; font-size: 0.85em; font-weight: 500; }
        
        .modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.7);
            z-index: 1000;
            align-items: center;
            justify-content: center;
        }
        
        .modal.active { display: flex; }
        
        .modal-content {
            background: white;
            padding: 30px;
            border-radius: 15px;
            max-width: 800px;
            max-height: 80vh;
            overflow-y: auto;
            position: relative;
        }
        
        .modal-close {
            position: absolute;
            top: 15px;
            right: 20px;
            font-size: 2em;
            cursor: pointer;
            color: #666;
        }
        
        .modal-title { font-size: 1.8em; color: #667eea; margin-bottom: 20px; }
        
        .workflow-full { display: flex; flex-direction: column; gap: 10px; }
        
        .workflow-step {
            display: flex;
            align-items: center;
            gap: 15px;
            padding: 15px;
            background: #f8f9fa;
            border-radius: 10px;
        }
        
        .workflow-step.completed { background: #d4edda; }
        .workflow-step.running { background: #cfe2ff; border: 2px solid #667eea; }
        
        .step-circle {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
        }
        
        .step-circle.completed { background: #28a745; color: white; }
        .step-circle.running { background: #667eea; color: white; }
        .step-circle.pending { background: #e9ecef; color: #666; }
        
        .step-details { flex: 1; }
        .step-title { font-weight: 600; margin-bottom: 5px; }
        .step-duration { color: #666; font-size: 0.85em; }
        
        .activity-log { display: flex; flex-direction: column; gap: 10px; }
        
        .activity-item {
            padding: 10px 15px;
            background: #f8f9fa;
            border-radius: 8px;
            border-left: 4px solid #667eea;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .activity-item.ok { border-left-color: #28a745; }
        .activity-time { color: #666; font-size: 0.85em; min-width: 80px; }
        .activity-agent { color: #667eea; font-weight: 600; }
        .activity-tool { font-weight: 500; }
        
        .activity-status {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 0.8em;
            font-weight: 600;
            margin-left: 8px;
        }
        
        .activity-status.ok { background: #d4edda; color: #155724; }
        
        .empty-state {
            text-align: center;
            padding: 40px;
            color: #999;
            font-size: 1.1em;
        }
        
        .refresh-info {
            text-align: center;
            padding: 15px;
            color: rgba(255,255,255,0.9);
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
        }
        
        .pulse {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #4ade80;
            animation: pulse 2s infinite;
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.3; }
        }
        
        /* Modal Tabs */
        .modal-tabs {
            display: flex;
            gap: 10px;
            margin-bottom: 20px;
            border-bottom: 2px solid #f0f0f0;
        }
        
        .modal-tab {
            padding: 10px 20px;
            cursor: pointer;
            border-bottom: 3px solid transparent;
            font-weight: 500;
            color: #666;
            transition: 0.2s;
        }
        
        .modal-tab:hover { color: #667eea; }
        .modal-tab.active {
            color: #667eea;
            border-bottom-color: #667eea;
        }
        
        .tab-content { display: none; }
        .tab-content.active { display: block; }
        
        /* Mermaid Diagram Viewer */
        .diagram-container {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 10px;
            overflow-x: auto;
            min-height: 400px;
        }
        
        /* Highlight current step in Mermaid diagram */
        .mermaid .current-step {
            filter: drop-shadow(0 0 10px #667eea);
            animation: glow 2s infinite;
        }
        
        @keyframes glow {
            0%, 100% { filter: drop-shadow(0 0 10px #667eea); }
            50% { filter: drop-shadow(0 0 20px #667eea); }
        }
    </style>
</head>
<body>
    <header>
        <h1>🤖 Pyjama Agent Dashboard</h1>
        <div class=\"subtitle\">Real-time monitoring of agent execution and metrics</div>
    </header>
    
    <div class=\"nav\">
        <div class=\"nav-item active\" onclick=\"switchView('metrics')\">📊 Metrics</div>
        <div class=\"nav-item\" onclick=\"switchView('agents')\">🤖 Active Agents</div>
        <div class=\"nav-item\" onclick=\"switchView('past-runs')\">📜 Past Runs</div>
        <div class=\"nav-item\" onclick=\"switchView('activity')\">📝 Activity</div>
    </div>
    
    <div class=\"container\">
        <div id=\"metrics-view\" class=\"view active\">
            <div class=\"card\">
                <div id=\"global-metrics\" class=\"metrics-grid\"></div>
            </div>
        </div>
        
        <div id=\"agents-view\" class=\"view\">
            <div class=\"card\">
                <div id=\"active-agents\"></div>
            </div>
        </div>
        
        
        <div id=\"past-runs-view\" class=\"view\">
            <div class=\"card\">
                <div id=\"past-runs\"></div>
            </div>
        </div>
        <div id=\"activity-view\" class=\"view\">
            <div class=\"card\">
                <div id=\"recent-logs\" class=\"activity-log\"></div>
            </div>
        </div>
        
        <div class=\"refresh-info\">
            <span class=\"pulse\"></span>
            Auto-refreshing every 2 seconds
        </div>
    </div>
    
    <div id=\"workflow-modal\" class=\"modal\" onclick=\"if (event.target === this) closeModal();\">
        <div class=\"modal-content\">
            <span class=\"modal-close\" onclick=\"closeModal()\">&times;</span>
            <h2 class=\"modal-title\" id=\"modal-agent-name\">Agent Workflow</h2>
            
            <div class=\"modal-tabs\">
                <div class=\"modal-tab active\" onclick=\"switchModalTab('steps')\">📋 Steps</div>
                <div class=\"modal-tab\" onclick=\"switchModalTab('diagram')\">📊 Diagram</div>
            </div>
            
            <div id=\"tab-steps\" class=\"tab-content active\">
                <div id=\"modal-workflow\" class=\"workflow-full\"></div>
            </div>
            
            <div id=\"tab-diagram\" class=\"tab-content\">
                <div id=\"diagram-container\" class=\"diagram-container\">
                    <div id=\"mermaid-diagram\"></div>
                </div>
            </div>
        </div>
    </div>
    
    <script>
        var currentView = 'metrics';
        
        function switchView(view) {
            currentView = view;
            var views = document.querySelectorAll('.view');
            for (var i = 0; i < views.length; i++) {
                views[i].classList.remove('active');
            }
            var navItems = document.querySelectorAll('.nav-item');
            for (var j = 0; j < navItems.length; j++) {
                navItems[j].classList.remove('active');
            }
            document.getElementById(view + '-view').classList.add('active');
            event.target.classList.add('active');
        }
        
        function formatTimestamp(ts) {
            return new Date(ts).toLocaleTimeString();
        }
        
        function formatDuration(ms) {
            if (ms < 1000) return ms.toFixed(2) + 'ms';
            if (ms < 60000) return (ms / 1000).toFixed(2) + 's';
            return Math.floor(ms / 60000) + 'm ' + Math.floor((ms % 60000) / 1000) + 's';
        }
        
        function showWorkflow(agentId, agentData) {
            // Store current agent info for diagram rendering
            currentAgentId = agentId;
            currentAgentData = agentData;
            
            var modal = document.getElementById('workflow-modal');
            var modalTitle = document.getElementById('modal-agent-name');
            var modalWorkflow = document.getElementById('modal-workflow');
            
            modalTitle.textContent = agentId + ' - Full Workflow';
            
            var steps = agentData.steps || [];
            var currentStep = agentData['current-step'];
            
            var html = '';
            for (var i = 0; i < steps.length; i++) {
                var step = steps[i];
                var isCompleted = step.status === 'ok' || step.status === 'completed';
                var isRunning = step.status === 'running' || step['step-id'] === currentStep;
                var isPending = !isCompleted && !isRunning;
                
                var statusClass = isPending ? 'pending' : (isCompleted ? 'completed' : 'running');
                var circleContent = isPending ? (i + 1) : (isCompleted ? '✓' : '▶');
                
                var duration = '';
                if (step['end-time'] && step['start-time']) {
                    duration = formatDuration(step['end-time'] - step['start-time']);
                } else if (isRunning && step['start-time']) {
                    duration = formatDuration(Date.now() - step['start-time']) + ' (running)';
                }
                
                html += '<div class=\"workflow-step ' + statusClass + '\">' +
                        '<div class=\"step-circle ' + statusClass + '\">' + circleContent + '</div>' +
                        '<div class=\"step-details\">' +
                        '<div class=\"step-title\">' + step['step-id'] + '</div>' +
                        (duration ? '<div class=\"step-duration\">' + duration + '</div>' : '') +
                        '</div></div>';
            }
            
            modalWorkflow.innerHTML = html;
            modal.classList.add('active');
        }
        
        function closeModal() {
            document.getElementById('workflow-modal').classList.remove('active');
        }
        
        function switchModalTab(tab) {
            // Update tab buttons
            var tabs = document.querySelectorAll('.modal-tab');
            for (var i = 0; i < tabs.length; i++) {
                tabs[i].classList.remove('active');
            }
            event.target.classList.add('active');
            
            // Update tab content
            var contents = document.querySelectorAll('.tab-content');
            for (var j = 0; j < contents.length; j++) {
                contents[j].classList.remove('active');
            }
            document.getElementById('tab-' + tab).classList.add('active');
            
            // If switching to diagram tab, render it
            if (tab === 'diagram') {
                renderMermaidDiagram();
            }
        }
        
        var currentAgentId = null;
        var currentAgentData = null;
        
        function renderMermaidDiagram() {
            if (!currentAgentId) return;
            
            var container = document.getElementById('mermaid-diagram');
            container.innerHTML = '<div style=\"text-align: center; padding: 40px; color: #999;\">Loading diagram...</div>';
            
            // Fetch the Mermaid diagram from the server
            fetch('/api/agent/' + encodeURIComponent(currentAgentId) + '/diagram')
                .then(function(res) { return res.text(); })
                .then(function(mermaidCode) {
                    // Create a unique ID for this diagram
                    var diagramId = 'mermaid-' + Date.now();
                    container.innerHTML = '<div id=\"' + diagramId + '\">' + mermaidCode + '</div>';
                    
                    // Render the mermaid diagram
                    mermaid.init(undefined, '#' + diagramId);
                    
                    // Highlight the current step after rendering
                    setTimeout(function() {
                        highlightCurrentStep();
                    }, 100);
                })
                .catch(function(err) {
                    container.innerHTML = '<div style=\"text-align: center; padding: 40px; color: #dc3545;\">Failed to load diagram: ' + err.message + '</div>';
                });
        }
        
        function highlightCurrentStep() {
            if (!currentAgentData) return;
            
            var currentStep = currentAgentData['current-step'];
            if (!currentStep) return;
            
            // Convert step name to node name format (replace hyphens with underscores)
            var nodeName = currentStep.replace(/[^a-zA-Z0-9_]/g, '_');
            
            // Find and highlight the current step node in the SVG
            var diagram = document.querySelector('#mermaid-diagram svg');
            if (!diagram) return;
            
            // Try to find the node by id
            var node = diagram.querySelector('#' + nodeName);
            if (!node) {
                // Try alternate selectors
                var nodes = diagram.querySelectorAll('g.node');
                for (var i = 0; i < nodes.length; i++) {
                    var nodeId = nodes[i].getAttribute('id');
                    if (nodeId && nodeId.includes(nodeName)) {
                        node = nodes[i];
                        break;
                    }
                }
            }
            
            if (node) {
                node.classList.add('current-step');
                // Scroll into view
                node.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
        
        function updateDashboard() {
            fetch('/api/data')
                .then(function(res) { return res.json(); })
                .then(function(data) {
                    var metrics = data.metrics.global || {};
                    
                    // Count currently running agents
                    var agents = data.agents || {};
                    var runningCount = 0;
                    for (var key in agents) {
                        if (agents[key].status === 'running') {
                            runningCount++;
                        }
                    }
                    
                    document.getElementById('global-metrics').innerHTML =
                        '<div class=\"metric\"><div class=\"metric-label\">Currently Running</div><div class=\"metric-value\">' + runningCount + '</div></div>' +
                        '<div class=\"metric\"><div class=\"metric-label\">Total Executions</div><div class=\"metric-value\">' + (metrics.count || 0) + '</div></div>' +
                        '<div class=\"metric\"><div class=\"metric-label\">Success Rate</div><div class=\"metric-value\">' + ((metrics['success-rate'] || 0) * 100).toFixed(1) + '%</div></div>' +
                        '<div class=\"metric\"><div class=\"metric-label\">Avg Duration</div><div class=\"metric-value\">' + formatDuration(metrics['avg-duration-ms'] || 0) + '</div></div>' +
                        '<div class=\"metric\"><div class=\"metric-label\">Throughput</div><div class=\"metric-value\">' + (metrics.throughput || 0).toFixed(2) + ' ops/sec</div></div>';
                    
                    // Separate running and completed agents
                    var runningAgents = [];
                    var completedAgents = [];
                    for (var key in agents) {
                        if (agents[key].status === 'running') {
                            runningAgents.push({id: key, data: agents[key]});
                        } else if (agents[key].status === 'completed') {
                            completedAgents.push({id: key, data: agents[key]});
                        }
                    }
                    
                    // Render Active Agents (running only)
                    if (runningAgents.length === 0) {
                        document.getElementById('active-agents').innerHTML = '<div class=\"empty-state\">No active agents</div>';
                    } else {
                        var agentsHTML = '';
                        for (var k = 0; k < runningAgents.length; k++) {
                            var agentId = runningAgents[k].id;
                            var agent = runningAgents[k].data;
                            var isCompleted = agent.status === 'completed';
                            var steps = agent.steps || [];
                            var currentStep = agent['current-step'];
                            var completedCount = 0;
                            for (var m = 0; m < steps.length; m++) {
                                if (steps[m].status === 'ok' || steps[m].status === 'completed') {
                                    completedCount++;
                                }
                            }
                            
                            var duration = 0;
                            if (agent['end-time'] && agent['start-time']) {
                                duration = agent['end-time'] - agent['start-time'];
                            } else if (agent['start-time']) {
                                duration = Date.now() - agent['start-time'];
                            }
                            
                            agentsHTML += '<div class=\"agent-card ' + (isCompleted ? 'completed' : '') + '\" data-agent-data=\"' + encodeURIComponent(JSON.stringify(agent)) + '\" data-agent-id=\"' + agentId + '\">'+
                                '<div class=\"agent-header\">' +
                                '<div class=\"agent-name\">' + agentId + '</div>' +
                                '<div class=\"agent-status ' + (isCompleted ? 'completed' : 'running') + '\">' + (isCompleted ? 'COMPLETED' : 'RUNNING') + '</div>' +
                                '</div>' +
                                '<div class=\"agent-meta\">Duration: ' + formatDuration(duration) + '</div>';
                            
                            if (currentStep) {
                                agentsHTML += '<div class=\"current-step-preview\">' +
                                    '<div class=\"step-indicator\">▶</div>' +
                                    '<div class=\"step-info\">' +
                                    '<div class=\"step-name\">' + currentStep + '</div>' +
                                    '<div class=\"step-count\">' + completedCount + ' of ' + steps.length + ' steps completed</div>' +
                                    '</div>' +
                                    '<div class=\"expand-hint\">Click to expand →</div>' +
                                    '</div>';
                            } else {
                                agentsHTML += '<div class=\"current-step-preview\">' +
                                    '<div class=\"step-indicator completed\">✓</div>' +
                                    '<div class=\"step-info\">' +
                                    '<div class=\"step-name\">All steps completed</div>' +
                                    '<div class=\"step-count\">' + steps.length + ' steps total</div>' +
                                    '</div>' +
                                    '<div class=\"expand-hint\">Click to view →</div>' +
                                    '</div>';
                            }
                            
                            agentsHTML += '</div>';
                        }
                        document.getElementById('active-agents').innerHTML = agentsHTML;
                    }
                    
                    
                    
                    var logs = data['recent-logs'] || [];
                    if (logs.length === 0) {
                        document.getElementById('recent-logs').innerHTML = '<div class=\"empty-state\">No recent activity</div>';
                    } else {
                        var logsHTML = '';
                        var recentLogs = logs.slice().reverse().slice(0, 20);
                        for (var n = 0; n < recentLogs.length; n++) {
                            var log = recentLogs[n];
                            var status = log.status || 'ok';
                            logsHTML += '<div class=\"activity-item ' + status + '\">' +
                                '<div class=\"activity-time\">' + formatTimestamp(log.timestamp) + '</div>' +
                                '<div>' +
                                '<span class=\"activity-agent\">' + log['agent-id'] + '</span> executed ' +
                                '<span class=\"activity-tool\">' + log.tool + '</span>' +
                                '<span class=\"activity-status ' + status + '\">' + status + '</span>' +
                                '</div></div>';
                        }
                        document.getElementById('recent-logs').innerHTML = logsHTML;
                    }
                })
                .catch(function(err) { console.error('Error:', err); });
        }
        
        
        // Event delegation for agent cards
        document.addEventListener('click', function(e) {
            var card = e.target.closest('.agent-card');
            if (card) {
                e.preventDefault();
                e.stopPropagation();
                var agentId = card.getAttribute('data-agent-id');
                var agentDataStr = card.getAttribute('data-agent-data');
                if (agentId && agentDataStr) {
                    try {
                        var agentData = JSON.parse(decodeURIComponent(agentDataStr));
                        showWorkflow(agentId, agentData);
                    } catch (err) {
                        console.error('Failed to parse agent data:', err);
                    }
                }
            }
        });
        
        
        // Initialize Mermaid
        mermaid.initialize({
            startOnLoad: false,
            theme: 'default',
            securityLevel: 'loose',
            flowchart: {
                useMaxWidth: true,
                htmlLabels: true
            }
        });
        
        updateDashboard();
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

    ;; New endpoint for agent diagrams
    (str/starts-with? request-line "GET /api/agent/")
    (let [;; Extract agent ID from URL: GET /api/agent/{id}/diagram HTTP/1.1
          path (second (str/split request-line #" "))
          parts (str/split path #"/")
          agent-id (when (>= (count parts) 4)
                     (keyword (java.net.URLDecoder/decode (nth parts 3) "UTF-8")))
          is-diagram? (and (>= (count parts) 5)
                           (= (nth parts 4) "diagram"))]
      (if (and agent-id is-diagram?)
        (try
          ;; Get agent spec from registry
          (let [registry @pyjama/agents-registry
                agent-spec (get registry agent-id)]
            (if agent-spec
              {:status 200
               :content-type "text/plain"
               :headers {"Cache-Control" "no-cache, no-store, must-revalidate"
                         "Pragma" "no-cache"
                         "Expires" "0"}
               :body (visualize/visualize-mermaid agent-id agent-spec)}
              {:status 404
               :content-type "text/plain"
               :body (str "Agent not found: " agent-id)}))
          (catch Exception e
            {:status 500
             :content-type "text/plain"
             :body (str "Error generating diagram: " (.getMessage e))}))
        {:status 400
         :content-type "text/plain"
         :body "Invalid agent diagram request"}))

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
