# 3. Register content of Capstone Project

# (*) 3.1. Capstone Project name

## 3.1.1. English Title

LuxMap: A GIS, IoT and Computer Vision Platform for Rural Road Lighting Asset and Fault Management

## 3.1.2. Vietnamese Title

LuxMap: Hệ thống bản đồ số GIS tích hợp IoT và thị giác máy tính để quản lý tài sản và sự cố chiếu sáng đường giao thông nông thôn

## Abbreviation

LuxMap

# (*) 3.2. Main proposal content (including result and product)

## a) Context

Rural road lighting in Vietnam has expanded rapidly under the new-rural-development programme, with communes installing thousands of grid-connected and standalone solar fixtures along inter-commune and inter-village roads. Installation, however, has outpaced management. A commune rarely holds an accurate inventory of what was installed, where, of what type, or when its warranty expires, and a fault is normally discovered only when a resident complains — which on a low-traffic rural road can take weeks. The problem is compounded by the dominant failure mode of solar fixtures, which do not simply switch off: as the battery ages the light still turns on at dusk but shuts down hours early, so any single night-time inspection finds it working while the road is dark for most of the night.

- **No reliable asset inventory:** Communes cannot state how many fixtures exist, their location, type, power source or warranty status, so maintenance cannot be planned or budgeted.
- **Faults are found by complaint, not by inspection:** Discovery depends on a resident reporting a dark stretch, which is slow and biased toward the busiest roads.
- **Degradation is invisible to on/off monitoring:** A dimming lamp, or a solar unit whose runtime has fallen from twelve hours to four, is failing yet registers as working in any binary check.
- **Instrumenting every pole is unaffordable:** A district may manage thousands of fixtures, so fitting an IoT node to each one exceeds any rural maintenance budget.
- **Segment-level causes are misdiagnosed as lamp faults:** A tripped breaker, failed timer or stolen cable darkens a whole stretch, but without spatial reasoning each dark pole is logged and dispatched separately.
- **Repairs are unprioritized:** Crews are mobilized one complaint at a time, with no basis for treating an unlit school approach or bridge as more urgent than a field road.

## b) Proposed Solutions

Build LuxMap, a GIS platform in which every fixture is a mapped asset with its own condition history, fed by two sensing channels sized to a rural budget. A sparse set of IoT nodes at segment controllers and on sampled fixtures reports power state, current draw and nightly operating duration; the remaining fixtures are covered by periodic night survey sweeps with a vehicle-mounted camera, whose imagery a computer vision engine analyses to detect each fixture, associate it with its pole, and grade it as normal, dim or out. Findings from both channels are written to the pole's record, clustered spatially to separate segment causes from lamp faults, scored for priority, and shown on the map.

- **GIS Lighting Asset Register:** Every pole and fixture mapped with type, power source, lamp specification, install date, warranty, owning commune, road segment and maintenance history.
- **Sparse IoT Monitoring Network:** Nodes reporting power state, current draw, ambient light and nightly on/off times, with store-and-forward buffering where rural coverage is intermittent.
- **Night Survey Capture:** A protocol for vehicle-mounted night imaging with locked exposure and recorded GPS, heading and camera parameters, so images from different nights remain photometrically comparable.
- **Computer Vision Detection Engine:** Detects illuminated fixtures, rejects confounding sources such as house lights, shop signs and oncoming headlights, and associates each detection with a pole using GPS, heading and GIS pole positions.
- **Per-Pole Luminance Baselining:** Normalizes each detection for exposure and standoff distance and compares it against that pole's own history, so dimming is assessed as change over time rather than against an absolute threshold.
- **Solar Runtime Analysis:** Tracks nightly operating duration and flags progressive shortening as an early indicator of battery end-of-life, before the fixture fails outright.
- **Spatial Fault Clustering:** Groups faults along a segment and against the electrical topology to infer an upstream cause rather than dispatching a crew per pole.
- **Priority Scoring and Work Orders:** Ranks confirmed faults by severity, road importance and proximity to schools, markets, bridges and junctions, then issues and tracks repairs to verified closure.

## c) Functional Requirements

- Management Agency
  - View the lighting network on a map with each fixture's status and outstanding faults
  - Monitor coverage and dark-spot analysis, and identify unlit junctions and bridges
  - Review the prioritized fault list and approve repair batches and budgets
  - Compare fault rates by commune, fixture type, supplier and installation year
  - Track fixtures approaching warranty expiry and export management reports
- Maintenance Engineer
  - Register road segments, poles and fixtures with type, power source, install date and warranty
  - Review detected faults on the map and confirm, reclassify or reject each finding
  - Inspect a fixture's condition history, including luminance and solar runtime trends
  - Confirm or override the inferred cause for a clustered segment fault
  - Create work orders from confirmed faults, group them geographically, and verify closure
- Field Surveyor and Repair Crew
  - Plan a night survey route, record capture metadata, and upload imagery after the sweep
  - Check coverage and image quality before submitting a sweep for processing
  - View assigned work orders with fixture location, fault type and reference imagery, and navigate to them
  - Record repair status and capture before-and-after evidence, operating offline and synchronizing later
  - Report additional faults observed on site
- IoT and AI Processing Engine
  - Ingest, validate and buffer node telemetry, and detect nodes that have stopped reporting
  - Detect illuminated fixtures in night imagery and reject non-fixture light sources
  - Associate each detection with a pole using GPS, heading and pole geometry
  - Normalize detected luminance for exposure and distance and compare against the pole's baseline
  - Classify each fixture as normal, dim or out, and compute solar runtime trends
  - Cluster faults spatially to infer segment-level causes, compute priority scores and raise alerts
- System Admin
  - Manage user accounts, roles and commune-level access permissions
  - Manage master data: fixture catalogue, fault types, severity thresholds and priority weights
  - Manage IoT device registration, firmware versions and AI model versions
  - Monitor processing jobs, storage consumption and platform health

## d) Non-Functional Requirements

- **Pole Association Accuracy:** Rural poles stand tens of metres apart, so a detection must be attributed to the correct pole; a fault mapped to the neighbouring pole sends the crew to the wrong asset.
- **Photometric Comparability:** Night captures must use locked exposure with recorded parameters, since auto-exposure brightens dark scenes and would mask exactly the dimming the system exists to detect.
- **Affordability per Fixture:** The design must remain viable at commune budgets, which requires sparse IoT plus camera sweeps rather than a node on every pole.
- **Tolerance of Intermittent Connectivity:** Nodes must buffer and forward telemetry through gaps in rural coverage, and the mobile field module must function fully offline.
- **Processing Turnaround:** A night sweep of a commune must be processed within hours, so results are actionable in the same working week.
- **Detection Accuracy Reported per Class:** Precision and recall must be reported separately for out and dim, since dim is the harder and more valuable case and an aggregate figure would hide it.
- **Field Usability:** Map, fault review and work order interfaces must be operable by commune technicians without GIS or machine learning background.
- **Data Retention and Auditability:** Imagery, telemetry and fault records retained through the warranty period, with an audit trail of every automated finding and engineer decision.
- **Operational Safety and Compliance:** Night survey procedures must define speed, route and safety rules for capture on live roads, and any drone-assisted capture must comply with Vietnamese unmanned aircraft regulations.

## e) Theory & Practical

### Theory

The project applies concepts from geographic information systems, low-light computer vision, photometry and asset condition monitoring.

- **GIS Asset and Network Data Models:** Representation of poles, fixtures, road segments and electrical feeders as spatial entities with topology, and spatial indexing for map query and proximity analysis.
- **Road Lighting Standards and Photometry:** Illuminance, luminance and uniformity, and the Vietnamese requirements for road lighting works, which define what counts as adequate rather than merely present lighting.
- **Low-Light Object Detection:** Deep learning detection on night imagery, and the specific difficulties of bloom, overexposure, motion blur and glare from oncoming vehicles.
- **Camera Exposure and Radiometric Normalization:** The relationship between exposure time, aperture, sensor gain and pixel intensity, and inverse-square falloff with distance — the basis for comparing brightness across nights and standoff distances.
- **Data Association:** Matching a sensed observation to the correct entity in a spatial database using position, heading and geometric constraints.
- **Photovoltaic and Battery State-of-Health:** Degradation of standalone solar lighting through capacity fade, panel soiling and shading, and its signature as progressively shortening nightly runtime.
- **Time-Series Baselining and Anomaly Detection:** Per-entity baseline construction and trend detection, distinguishing genuine degradation from night-to-night variation in weather and capture conditions.
- **Spatial Clustering and Multi-Criteria Prioritization:** Density-based clustering over the road and feeder topology for root-cause inference, and weighted scoring combining fault severity, road importance and exposure of vulnerable locations.

### Practical

These concepts are applied by building and validating the platform on real rural roads with a partner commune or district transport office.

- Build the GIS lighting asset register for a pilot area through an initial survey of pole positions, fixture types and power sources.
- Design and assemble the IoT node — power state and current sensing, ambient light sensing, real-time clock and a low-power wide-area radio — and deploy it at segment controllers and on sampled fixtures.
- Define the night survey protocol: route, speed, camera mounting, locked exposure, and GPS and heading logging.
- Build an annotated night imagery dataset covering normal, dim and out fixtures of both grid and solar type, with confounding light sources labelled.
- Train and evaluate the detection model, and implement pole association from GPS, heading and GIS pole geometry.
- Implement exposure and distance normalization and per-pole baselining, then validate dim classification against handheld lux meter readings on site.
- Implement solar runtime tracking and the trend rule flagging shortening runtime as approaching battery failure.
- Implement spatial clustering for cause inference and the priority scoring model agreed with the partner agency.
- Build the web GIS dashboard, the offline mobile field module and the work order workflow.
- Run a field trial across a pilot network, comparing platform findings against manual night inspection of the same fixtures.

## f) Products (Expected Deliverables)

- **LuxMap Web GIS Platform:** Map-based asset register, fault review and confirmation, coverage and dark-spot analysis, prioritization and repair planning.
- **Mobile Field Module:** Offline-capable survey capture, work order execution, navigation to fixtures and before-and-after repair evidence.
- **IoT Node and Firmware:** Power state, current and ambient light sensing with nightly runtime reporting and store-and-forward buffering over a low-power wide-area network.
- **Backend, Spatial Database and Telemetry Ingestion:** Asset, survey, telemetry, fault, work order and evidence domains with role-based access and an audit trail.
- **Computer Vision Detection Engine:** Night fixture detection, confounding-source rejection, pole association, exposure and distance normalization, and normal/dim/out classification.
- **Fault Analysis and Prioritization Engine:** Per-pole baselining, solar runtime trend analysis, spatial clustering for cause inference and multi-criteria priority scoring.
- **Annotated Night Lighting Dataset:** Labelled imagery covering normal, dim and out fixtures with paired lux meter ground truth for a validation subset.
- **Night Survey Procedure Package:** Documented capture protocol, camera settings, route planning and field safety rules.
- **Field Trial and Evaluation Report:** Detection accuracy per class, dim classification agreement with lux measurement, solar runtime findings and agency usability feedback.

## g) Proposed Tasks

Team of 4 members over one semester of approximately fourteen to sixteen weeks, each owning one Work Package, with shared responsibility for field survey campaigns, integration and the final trial.

- **WP1 – Project Management, Domain Research and Field Data Collection (Role: Project Manager / Business Analyst):** Scope and requirements with the partner agency, lighting standards and rural practice research, GIS and asset data model, survey protocol design, and field campaign and trial execution.
- **WP2 – IoT Node, Backend and Spatial Services (Role: Backend / Embedded Developer):** IoT node hardware and firmware, telemetry ingestion and buffering, spatial database, asset and work order services, audit trail and access control.
- **WP3 – Computer Vision and Fault Analysis Engine (Role: AI / Computer Vision Engineer):** Night detection model, pole association, exposure and distance normalization, per-pole baselining, solar runtime analysis, spatial clustering and priority scoring.
- **WP4 – Web GIS Dashboard and Mobile Field Module (Role: Frontend / Mobile Developer):** Map dashboard, fault review workflow, coverage analysis and repair planning views, offline mobile module, and usability testing with agency staff.

# 3.3. Research Information

## Research Description

### a. Research Problem / Research Question

Detecting that a street light is off is easy; detecting that it is failing is not, and failing is the state that matters for maintenance planning. The project asks two questions. First, whether normalizing detected fixture brightness for camera exposure and standoff distance and comparing it against that fixture's own historical baseline enables reliable identification of dim fixtures from vehicle-mounted night imagery, and how the resulting classification agrees with handheld lux meter measurement — a comparison that also quantifies whether locked-exposure capture is genuinely necessary. Second, given that standalone solar fixtures fail by progressively shortening their nightly operating duration rather than by switching off, whether runtime measured from a sparse IoT subset and successive sweeps declines predictably enough to serve as a leading indicator of battery end-of-life, and how far in advance such a decline becomes detectable.

### b. Research Objectives

- Construct an annotated night imagery dataset of rural road lighting covering normal, dim and out fixtures of both grid and solar type, with confounding light sources labelled and lux meter ground truth for a validation subset.
- Evaluate a baseline model classifying fixtures as on or off, reporting precision and recall per class against manual night inspection.
- Evaluate the normalized per-pole baselining approach for dim detection, quantifying its improvement over the on/off baseline and its agreement with lux measurement.
- Quantify the contribution of locked exposure by comparing performance on locked-exposure and auto-exposure captures of the same fixtures.
- Test whether nightly runtime of solar fixtures declines measurably before failure, and estimate the lead time such a decline provides.
- Assess operational feasibility by recording survey, processing and review effort per kilometre against manual night inspection.

### c. Research Scope & Methodology

A quantitative field study on rural road sections in a partner commune or district, bounded to pole-mounted grid and standalone solar fixtures and to capture achievable with a low-cost vehicle-mounted camera. Ground truth is established by an engineer inspecting each sampled fixture at night, recording its state and measuring illuminance beneath it with a handheld lux meter. The same sections are surveyed by vehicle under recorded capture parameters, with a subset captured under both locked and automatic exposure on the same night so the two can be compared directly. Detection and classification are evaluated on held-out sections with per-class precision, recall and F1 reported, and dim classification compared against lux measurements using standard agreement statistics. Solar runtime is tracked over the study period from the IoT subset and repeated sweeps, and its decline analysed against observed failures. Scope limits are explicit: the study covers a single pilot network over one semester, so seasonal effects and long-run battery ageing are only partially observable, and results are indicative rather than generalizable across suppliers.

### d. Expected Scientific Contribution

An empirical evaluation of whether low-cost vehicle-mounted night imagery, normalized for exposure and distance and baselined per fixture, can turn street lighting inspection from a binary on/off check into a graded condition assessment — with a reported agreement figure against lux measurement, which is rarely published for the non-photometric-grade equipment a commune could actually afford. Alongside it, an empirical test of nightly runtime shortening as a leading indicator of solar battery failure, which if supported converts solar lighting maintenance from reactive replacement to scheduled intervention. The project also produces an annotated rural night lighting dataset with paired lux ground truth, and a reference architecture combining sparse IoT with periodic camera sweeps for asset monitoring under a budget that cannot instrument every asset.

### e. Related Works / Literature Review (Preliminary)

- Research on smart street lighting and IoT-based lighting control and monitoring, and their reported cost and coverage limitations.
- Work on street light fault detection from vehicle-mounted or aerial night imagery using deep learning object detection.
- Literature on low-light and night-time computer vision, including exposure handling, glare and bloom in night scenes.
- Studies on radiometric normalization and photometric comparison across captures with varying exposure and geometry.
- Research on photovoltaic and battery state-of-health estimation for standalone solar lighting, including capacity fade and soiling.
- Vietnamese standards and regulations on road lighting works, defining illuminance and uniformity requirements.
- Work on GIS-based infrastructure asset management and condition monitoring for public works.
- Literature on spatial clustering for root-cause inference in networked infrastructure faults, and on multi-criteria maintenance prioritization.

# 4. Other Comments

## Other Comments

- **The sparse-IoT plus camera-sweep architecture is the point, not a compromise:** A team may be tempted to design a node for every pole, which is technically simpler and economically impossible at commune scale. The contribution is covering thousands of fixtures with a small number of nodes plus repeatable imaging, so cost per monitored fixture should be an explicit, reported figure.
- **Dim is the hard case and the valuable one:** Binary on/off detection is close to solved and would make a weak capstone. What distinguishes this topic is grading degradation, which requires locked exposure, distance normalization and per-fixture baselining. These are mandatory from the start, since auto-exposure imagery cannot be corrected afterwards.
- **Scope risk lies in field access and night operations, not in software:** The project depends on a cooperating commune or district office, permission to survey at night, and fixtures at varied ages and condition levels. These should be secured before the schedule commits to them, with initial model development able to start on publicly available night imagery so neither access nor weather can block the AI work.
