# POC 24 — generation approaches comparison

## Blueprint: Aurora (44294 chars)

=== Aurora / single-shot flat ===
process: aurora_pharma_distribution
agents: 1 | steps: 10 | shared types: 0 | connected: true
verdict: VALID
  - aurora_pharma_distribution [goal: ClosedOrderRecord] 10 steps, 10 types
      * order_intake_and_validation: [UserInput] -> ValidatedOrder
      * customer_authorization_check: [ValidatedOrder] -> CustomerAuthorizationResult
      * regulatory_product_validation: [ValidatedOrder] -> RegulatoryValidationResult
      * commercial_credit_validation: [ValidatedOrder, CustomerAuthorizationResult, RegulatoryValidationResult] -> CommerciallyApprovedOrder
      * inventory_allocation: [CommerciallyApprovedOrder] -> InventoryAllocationPlan
      * warehouse_fulfilment: [InventoryAllocationPlan] -> ReadyShipmentManifest
      * transportation_planning: [ReadyShipmentManifest] -> DispatchedShipment
      * shipment_monitoring_and_exception_handling: [DispatchedShipment] -> DeliveryAttemptRecord
      * delivery_receipt_confirmation: [DeliveryAttemptRecord] -> ConfirmedDelivery
      * financial_reconciliation_and_closure: [ConfirmedDelivery] -> ClosedOrderRecord

=== Aurora / embabel flat ===
process: Aurora
agents: 1 | steps: 1 | shared types: 0 | connected: false
verdict: INVALID (2 issues)
  - Aurora [goal: OrderCompletionStatus] 1 steps, 2 types
      * processPurchaseOrder: [PurchaseOrder] -> OrderCompletionStatus
  ! agent 'Aurora' goal output 'OrderCompletionStatus' is not reachable from the process entry.
  ! agent 'Aurora' step 'processPurchaseOrder' needs input 'PurchaseOrder', which no agent produces.

=== Aurora / single-shot multi ===
process: pharmaceutical_order_to_delivery
agents: 8 | steps: 12 | shared types: 7 | connected: true
verdict: VALID
  - order_intake_agent [goal: ValidatedOrderRecord] 1 steps, 1 types
      * extract_and_validate_order: [UserInput] -> ValidatedOrderRecord
  - regulatory_compliance_agent [goal: RegulatoryClearance] 3 steps, 4 types
      * verify_customer_authorization: [ValidatedOrderRecord] -> CustomerAuthorizationStatus
      * validate_product_regulations: [ValidatedOrderRecord] -> ProductRegulatoryStatus
      * synthesize_regulatory_decision: [CustomerAuthorizationStatus, ProductRegulatoryStatus] -> RegulatoryClearance
  - commercial_validation_agent [goal: CommercialApproval] 1 steps, 3 types
      * assess_credit_and_pricing: [ValidatedOrderRecord, RegulatoryClearance] -> CommercialApproval
  - inventory_allocation_agent [goal: InventoryAllocationPlan] 1 steps, 3 types
      * search_and_allocate_inventory: [ValidatedOrderRecord, CommercialApproval] -> InventoryAllocationPlan
  - warehouse_fulfilment_agent [goal: DispatchReadyShipment] 2 steps, 3 types
      * pick_and_verify_products: [InventoryAllocationPlan] -> VerifiedPickList
      * package_and_release_quality: [VerifiedPickList] -> DispatchReadyShipment
  - logistics_transportation_agent [goal: InTransitShipment] 2 steps, 3 types
      * select_carrier_and_book: [DispatchReadyShipment] -> CarrierBooking
      * confirm_dispatch_handover: [DispatchReadyShipment, CarrierBooking] -> InTransitShipment
  - delivery_confirmation_agent [goal: ConfirmedDeliveryReceipt] 1 steps, 2 types
      * process_receiving_confirmation: [InTransitShipment] -> ConfirmedDeliveryReceipt
  - financial_reconciliation_agent [goal: FinalInvoice] 1 steps, 3 types
      * generate_invoice: [ValidatedOrderRecord, ConfirmedDeliveryReceipt] -> FinalInvoice

=== Aurora / embabel multi ===
process: aurora_pharmaceutical_distribution
agents: 11 | steps: 22 | shared types: 6 | connected: false
verdict: INVALID (18 issues)
  - order_intake_agent [goal: OrderRecord] 3 steps, 5 types
      * validateOrderData: [RawPurchaseOrder] -> ValidationResult
      * initiateClarification: [ValidationResult, RawPurchaseOrder] -> ClarificationRequest
      * assignPriorityAndProduceOrder: [RawPurchaseOrder, ValidationResult] -> OrderRecord
  - regulatory_compliance_agent [goal: RegulatoryValidationResult] 1 steps, 2 types
      * validate_regulatory_compliance: [OrderRecord] -> RegulatoryValidationResult
  - commercial_validation_agent [goal: CommercialApprovalStatus] 1 steps, 3 types
      * validate_commercial_terms: [OrderRecord, RegulatoryValidationResult] -> CommercialApprovalStatus
  - inventory_allocation_agent [goal: InventoryAllocationPlan] 1 steps, 4 types
      * search_inventory: [CommercialApprovalStatus, OrderRecord, RegulatoryValidationResult] -> InventoryAllocationPlan
  - warehouse_fulfilment_agent [goal: QualityReleasedPackage] 3 steps, 4 types
      * pick_and_verify: [InventoryAllocationPlan] -> VerifiedPickList
      * assess_packaging: [VerifiedPickList] -> PackagingAssessment
      * pack_and_release: [VerifiedPickList, PackagingAssessment] -> QualityReleasedPackage
  - logistics_transportation_agent [goal: CarrierBooking] 1 steps, 2 types
      * select_transportation_provider: [ShipmentReadyForDispatch] -> CarrierBooking
  - shipment_monitoring_agent [goal: ShipmentStatusUpdate] 1 steps, 2 types
      * evaluate_shipment_conditions: [InTransitShipment] -> ShipmentStatusUpdate
  - quality_assurance_agent [goal: QualityDispositionDecision] 2 steps, 3 types
      * investigateShipmentExcursion: [ShipmentStatusUpdate] -> QualityInvestigationReport
      * makeQualityDispositionDecision: [QualityInvestigationReport] -> QualityDispositionDecision
  - delivery_receipt_agent [goal: DeliveryConfirmationRecord] 3 steps, 4 types
      * verify_physical_receipt: [DeliveryArrival] -> PhysicalVerificationResult
      * process_electronic_confirmation: [PhysicalVerificationResult] -> ElectronicConfirmationRecord
      * finalize_delivery_confirmation: [PhysicalVerificationResult, ElectronicConfirmationRecord] -> DeliveryConfirmationRecord
  - financial_reconciliation_agent [goal: FinancialReconciliationReport] 1 steps, 3 types
      * generate_invoices_and_reconcile_shipments: [DeliveryConfirmationRecord, OrderRecord] -> FinancialReconciliationReport
  - incident_management_agent [goal: IncidentResolutionRecord] 5 steps, 6 types
      * analyze_incident: [IncidentReport] -> IncidentAnalysis
      * develop_response_plan: [IncidentAnalysis] -> ResponsePlan
      * execute_response: [ResponsePlan] -> ExecutionStatus
      * investigate_and_verify: [ExecutionStatus] -> InvestigationResults
      * produce_resolution_record: [InvestigationResults] -> IncidentResolutionRecord
  ! agent 'order_intake_agent' goal output 'OrderRecord' is not reachable from the process entry.
  ! agent 'regulatory_compliance_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'commercial_validation_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'inventory_allocation_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'warehouse_fulfilment_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'logistics_transportation_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'shipment_monitoring_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'quality_assurance_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'delivery_receipt_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'financial_reconciliation_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'incident_management_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'order_intake_agent' step 'validateOrderData' needs input 'RawPurchaseOrder', which no agent produces.
  ! agent 'order_intake_agent' step 'initiateClarification' needs input 'RawPurchaseOrder', which no agent produces.
  ! agent 'order_intake_agent' step 'assignPriorityAndProduceOrder' needs input 'RawPurchaseOrder', which no agent produces.
  ! agent 'logistics_transportation_agent' step 'select_transportation_provider' needs input 'ShipmentReadyForDispatch', which no agent produces.
  ! agent 'shipment_monitoring_agent' step 'evaluate_shipment_conditions' needs input 'InTransitShipment', which no agent produces.
  ! agent 'delivery_receipt_agent' step 'verify_physical_receipt' needs input 'DeliveryArrival', which no agent produces.
  ! agent 'incident_management_agent' step 'analyze_incident' needs input 'IncidentReport', which no agent produces.

## Blueprint: Sentinel (31663 chars)

=== Sentinel / single-shot flat ===
process: sentinel_security_operations
agents: 1 | steps: 10 | shared types: 0 | connected: true
verdict: VALID
  - sentinel_security_operations [goal: IncidentClosureReport] 10 steps, 10 types
      * detect_and_triage_threat: [UserInput] -> SecurityAlert
      * investigate_threat: [SecurityAlert, ThreatIntelligenceReport, ForensicEvidenceReport, BusinessRiskAssessment] -> InvestigationReport
      * gather_external_intelligence: [SecurityAlert] -> ThreatIntelligenceReport
      * preserve_digital_evidence: [SecurityAlert] -> ForensicEvidenceReport
      * assess_business_risk: [SecurityAlert] -> BusinessRiskAssessment
      * plan_containment_response: [InvestigationReport, BusinessRiskAssessment] -> ContainmentPlan
      * execute_and_verify_containment: [ContainmentPlan, ForensicEvidenceReport] -> ContainmentVerification
      * eradicate_and_recover_systems: [ContainmentVerification, BusinessRiskAssessment] -> RecoveryValidation
      * finalize_regulatory_reporting: [InvestigationReport, ForensicEvidenceReport] -> ComplianceReport
      * close_incident_case: [InvestigationReport, ContainmentVerification, RecoveryValidation, ComplianceReport] -> IncidentClosureReport

=== Sentinel / embabel flat ===
process: Sentinel
agents: 1 | steps: 5 | shared types: 0 | connected: false
verdict: INVALID (2 issues)
  - Sentinel [goal: void] 5 steps, 6 types
      * analyzeSecurityEvent: [SecurityEvent] -> SecurityAlert
      * orchestrateInvestigation: [SecurityAlert] -> OperationalCase
      * assessIncidentRisk: [OperationalCase] -> RiskAssessmentReport
      * planIncidentResponse: [RiskAssessmentReport] -> ResponsePlan
      * executeContainment: [ResponsePlan] -> void
  ! agent 'Sentinel' goal output 'void' is not reachable from the process entry.
  ! agent 'Sentinel' step 'analyzeSecurityEvent' needs input 'SecurityEvent', which no agent produces.

=== Sentinel / single-shot multi ===
process: sentinel_security_operations
agents: 8 | steps: 18 | shared types: 7 | connected: false
verdict: INVALID (4 issues)
  - watchtower_agent [goal: SecurityAlert] 2 steps, 2 types
      * ingest_and_normalize_events: [UserInput] -> NormalizedEvents
      * classify_threats: [NormalizedEvents] -> SecurityAlert
  - orchestrator_agent [goal: SecurityCase] 3 steps, 3 types
      * create_security_case: [SecurityAlert] -> SecurityCase
      * enrich_with_intelligence: [SecurityCase] -> EnrichedSecurityCase
      * correlate_and_dispatch: [EnrichedSecurityCase] -> SecurityCase
  - investigator_agent [goal: InvestigationReport] 3 steps, 7 types
      * plan_investigation: [SecurityCase] -> InvestigationPlan
      * correlate_evidence: [InvestigationPlan, ForensicEvidence, ThreatIntelContext, BusinessImpactAssessment] -> CorrelatedFindings
      * finalize_investigation: [CorrelatedFindings] -> InvestigationReport
  - forensics_agent [goal: ForensicEvidence] 2 steps, 3 types
      * collect_and_preserve_evidence: [SecurityCase] -> RawEvidence
      * validate_and_analyze: [RawEvidence] -> ForensicEvidence
  - intel_agent [goal: ThreatIntelContext] 1 steps, 2 types
      * query_threat_feeds: [SecurityCase] -> ThreatIntelContext
  - risk_assessment_agent [goal: RiskAssessment] 3 steps, 4 types
      * assess_business_impact: [InvestigationReport] -> BusinessImpactAssessment
      * evaluate_regulatory_exposure: [BusinessImpactAssessment] -> RegulatoryExposure
      * calculate_final_risk: [BusinessImpactAssessment, RegulatoryExposure] -> RiskAssessment
  - responder_agent [goal: ContainmentResult] 2 steps, 4 types
      * plan_containment: [InvestigationReport, RiskAssessment] -> ContainmentPlan
      * execute_approved_actions: [ContainmentPlan] -> ContainmentResult
  - compliance_agent [goal: GovernanceReport] 2 steps, 5 types
      * prepare_regulatory_report: [InvestigationReport, ForensicEvidence, RiskAssessment] -> DraftRegulatoryReport
      * finalize_governance: [DraftRegulatoryReport] -> GovernanceReport
  ! agent 'investigator_agent' goal output 'InvestigationReport' is not reachable from the process entry.
  ! agent 'risk_assessment_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'responder_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'compliance_agent' goal output 'GovernanceReport' is not reachable from the process entry.

=== Sentinel / embabel multi ===
process: sentinel_security_operations
agents: 8 | steps: 13 | shared types: 3 | connected: false
verdict: INVALID (16 issues)
  - watchtower [goal: ThreatAlert] 2 steps, 3 types
      * normalizeAndDeduplicate: [SecurityEvent] -> NormalizedSecurityEvent
      * classifySeverity: [NormalizedSecurityEvent] -> ThreatAlert
  - orchestrator [goal: OperationalCase] 1 steps, 2 types
      * createOperationalCase: [ThreatAlert] -> OperationalCase
  - investigator [goal: InvestigationReport] 2 steps, 3 types
      * correlate_evidence: [OperationalCase] -> EvidenceCorrelation
      * produce_investigation_report: [OperationalCase, EvidenceCorrelation] -> InvestigationReport
  - intel [goal: ThreatIntelligenceBrief] 1 steps, 2 types
      * gatherThreatIntel: [IntelligenceRequest] -> ThreatIntelligenceBrief
  - forensics [goal: ForensicReport] 2 steps, 3 types
      * preserveEvidence: [EvidenceCollectionRequest] -> PreservedEvidence
      * analyzeEvidence: [PreservedEvidence] -> ForensicReport
  - sentinel_risk [goal: RiskAssessment] 1 steps, 3 types
      * assessRisk: [InvestigationFindings, AffectedAssetList] -> RiskAssessment
  - compliance [goal: GovernanceAssessment] 1 steps, 3 types
      * assessCompliance: [IncidentDetails, ForensicFindings] -> GovernanceAssessment
  - responder [goal: ResponseExecutionLog] 3 steps, 5 types
      * prepareContainmentPlan: [RiskAssessment, InvestigationResults] -> ContainmentPlan
      * executeAuthorizedActions: [ContainmentPlan] -> ExecutionLog
      * coordinateHighImpactActions: [ExecutionLog, ContainmentPlan] -> ResponseExecutionLog
  ! agent 'watchtower' goal output 'ThreatAlert' is not reachable from the process entry.
  ! agent 'orchestrator' is not connected to the process (no earlier agent feeds it).
  ! agent 'investigator' is not connected to the process (no earlier agent feeds it).
  ! agent 'intel' is not connected to the process (no earlier agent feeds it).
  ! agent 'forensics' is not connected to the process (no earlier agent feeds it).
  ! agent 'sentinel_risk' is not connected to the process (no earlier agent feeds it).
  ! agent 'compliance' is not connected to the process (no earlier agent feeds it).
  ! agent 'responder' is not connected to the process (no earlier agent feeds it).
  ! agent 'watchtower' step 'normalizeAndDeduplicate' needs input 'SecurityEvent', which no agent produces.
  ! agent 'intel' step 'gatherThreatIntel' needs input 'IntelligenceRequest', which no agent produces.
  ! agent 'forensics' step 'preserveEvidence' needs input 'EvidenceCollectionRequest', which no agent produces.
  ! agent 'sentinel_risk' step 'assessRisk' needs input 'InvestigationFindings', which no agent produces.
  ! agent 'sentinel_risk' step 'assessRisk' needs input 'AffectedAssetList', which no agent produces.
  ! agent 'compliance' step 'assessCompliance' needs input 'IncidentDetails', which no agent produces.
  ! agent 'compliance' step 'assessCompliance' needs input 'ForensicFindings', which no agent produces.
  ! agent 'responder' step 'prepareContainmentPlan' needs input 'InvestigationResults', which no agent produces.

## Blueprint: TradingAgents (71701 chars)

=== TradingAgents / single-shot flat ===
process: trading_agents_analysis
agents: 1 | steps: 8 | shared types: 0 | connected: true
verdict: VALID
  - trading_agents_analysis [goal: FinalDecision] 8 steps, 8 types
      * market_technical_analysis: [UserInput] -> TechnicalReport
      * sentiment_analysis: [UserInput] -> SentimentReport
      * news_macro_analysis: [UserInput] -> NewsMacroReport
      * fundamentals_analysis: [UserInput] -> FundamentalsReport
      * research_debate: [TechnicalReport, SentimentReport, NewsMacroReport, FundamentalsReport] -> InvestmentPlan
      * trader_proposal: [InvestmentPlan, TechnicalReport, UserInput] -> TransactionProposal
      * risk_debate: [TransactionProposal, TechnicalReport, SentimentReport, NewsMacroReport, FundamentalsReport, UserInput] -> RiskDebateSummary
      * portfolio_manager_decision: [InvestmentPlan, TransactionProposal, RiskDebateSummary, UserInput] -> FinalDecision

=== TradingAgents / embabel flat ===
process: TradingAgents
agents: 1 | steps: 1 | shared types: 0 | connected: false
verdict: INVALID (2 issues)
  - TradingAgents [goal: InvestmentDecision] 1 steps, 2 types
      * runTradingAnalysis: [RunConfiguration] -> InvestmentDecision
  ! agent 'TradingAgents' goal output 'InvestmentDecision' is not reachable from the process entry.
  ! agent 'TradingAgents' step 'runTradingAnalysis' needs input 'RunConfiguration', which no agent produces.

=== TradingAgents / single-shot multi ===
process: single_instrument_analysis
agents: 5 | steps: 15 | shared types: 4 | connected: false
verdict: INVALID (4 issues)
  - analyst_team_agent [goal: AnalystReportsBundle] 6 steps, 6 types
      * market_technical_analysis: [UserInput] -> TechnicalReport
      * sentiment_analysis: [UserInput] -> SentimentReport
      * news_macro_analysis: [UserInput] -> NewsMacroReport
      * fundamentals_analysis: [UserInput] -> FundamentalsReport
      * load_historical_lessons: [UserInput] -> HistoricalLessons
      * consolidate_reports: [TechnicalReport, SentimentReport, NewsMacroReport, FundamentalsReport, HistoricalLessons] -> AnalystReportsBundle
  - research_debate_agent [goal: InvestmentPlan] 3 steps, 3 types
      * bull_researcher_case: [AnalystReportsBundle, BearArgument] -> BullArgument
      * bear_researcher_case: [AnalystReportsBundle, BullArgument] -> BearArgument
      * research_manager_verdict: [AnalystReportsBundle, BullArgument, BearArgument] -> InvestmentPlan
  - trading_execution_agent [goal: TransactionProposal] 1 steps, 1 types
      * generate_transaction_proposal: [InvestmentPlan, AnalystReportsBundle, UserInput] -> TransactionProposal
  - risk_assessment_agent [goal: RiskDebateSummary] 4 steps, 4 types
      * aggressive_risk_review: [TransactionProposal, AnalystReportsBundle, ConservativeRiskView, NeutralRiskView] -> AggressiveRiskView
      * conservative_risk_review: [TransactionProposal, AnalystReportsBundle, AggressiveRiskView, NeutralRiskView] -> ConservativeRiskView
      * neutral_risk_review: [TransactionProposal, AnalystReportsBundle, AggressiveRiskView, ConservativeRiskView] -> NeutralRiskView
      * synthesize_risk_debate: [AggressiveRiskView, ConservativeRiskView, NeutralRiskView] -> RiskDebateSummary
  - portfolio_decision_agent [goal: FinalDecision] 1 steps, 1 types
      * make_final_decision: [InvestmentPlan, TransactionProposal, RiskDebateSummary, AnalystReportsBundle, UserInput] -> FinalDecision
  ! agent 'research_debate_agent' goal output 'InvestmentPlan' is not reachable from the process entry.
  ! agent 'trading_execution_agent' goal output 'TransactionProposal' is not reachable from the process entry.
  ! agent 'risk_assessment_agent' goal output 'RiskDebateSummary' is not reachable from the process entry.
  ! agent 'portfolio_decision_agent' goal output 'FinalDecision' is not reachable from the process entry.

=== TradingAgents / embabel multi ===
process: single_instrument_analysis
agents: 8 | steps: 9 | shared types: 7 | connected: false
verdict: INVALID (9 issues)
  - run_configuration_agent [goal: RunConfiguration] 1 steps, 2 types
      * gather_and_validate_run_parameters: [OperatorRequest] -> RunConfiguration
  - instrument_identity_resolver [goal: InstrumentIdentity] 1 steps, 2 types
      * resolve_instrument_identity: [RunConfiguration] -> InstrumentIdentity
  - parallel_analyst_team [goal: AnalystReportsBundle] 1 steps, 3 types
      * orchestrateAnalysts: [RunConfiguration, InstrumentIdentity] -> AnalystReportsBundle
  - research_debate_manager [goal: InvestmentPlan] 2 steps, 3 types
      * manageDebate: [AnalystReportsBundle] -> DebateTranscript
      * issueInvestmentPlan: [AnalystReportsBundle, DebateTranscript] -> InvestmentPlan
  - transaction_proposal_agent [goal: TransactionProposal] 1 steps, 3 types
      * create_transaction_proposal: [InvestmentPlan, AnalystReportsBundle] -> TransactionProposal
  - risk_debate_manager [goal: RiskDebateRecord] 1 steps, 3 types
      * runRiskDebate: [TransactionProposal, AnalystReportsBundle] -> RiskDebateRecord
  - portfolio_decision_maker [goal: FinalDecision] 1 steps, 5 types
      * make_final_decision: [InvestmentPlan, TransactionProposal, AnalystReportsBundle, RiskDebateRecord] -> FinalDecision
  - decision_recorder_and_reporter [goal: RunReport] 1 steps, 2 types
      * record_decision_and_generate_report: [FinalDecision] -> RunReport
  ! agent 'run_configuration_agent' goal output 'RunConfiguration' is not reachable from the process entry.
  ! agent 'instrument_identity_resolver' is not connected to the process (no earlier agent feeds it).
  ! agent 'parallel_analyst_team' is not connected to the process (no earlier agent feeds it).
  ! agent 'research_debate_manager' is not connected to the process (no earlier agent feeds it).
  ! agent 'transaction_proposal_agent' is not connected to the process (no earlier agent feeds it).
  ! agent 'risk_debate_manager' is not connected to the process (no earlier agent feeds it).
  ! agent 'portfolio_decision_maker' is not connected to the process (no earlier agent feeds it).
  ! agent 'decision_recorder_and_reporter' is not connected to the process (no earlier agent feeds it).
  ! agent 'run_configuration_agent' step 'gather_and_validate_run_parameters' needs input 'OperatorRequest', which no agent produces.
