-- Replace app_user UUID FKs with ccgid strings, then drop app_user.

ALTER TABLE tms_session DROP CONSTRAINT IF EXISTS tms_session_user_id_fkey;
ALTER TABLE toolkit DROP CONSTRAINT IF EXISTS toolkit_created_by_fkey;
ALTER TABLE toolkit DROP CONSTRAINT IF EXISTS toolkit_deleted_by_fkey;
ALTER TABLE toolkit DROP CONSTRAINT IF EXISTS toolkit_owner_user_id_fkey;
ALTER TABLE toolkit DROP CONSTRAINT IF EXISTS toolkit_updated_by_fkey;
ALTER TABLE toolkit_subtask DROP CONSTRAINT IF EXISTS toolkit_subtask_created_by_fkey;
ALTER TABLE toolkit_subtask DROP CONSTRAINT IF EXISTS toolkit_subtask_deleted_by_fkey;
ALTER TABLE toolkit_subtask DROP CONSTRAINT IF EXISTS toolkit_subtask_updated_by_fkey;
ALTER TABLE toolkit_shared_kpi_selection DROP CONSTRAINT IF EXISTS toolkit_shared_kpi_selection_created_by_fkey;
ALTER TABLE toolkit_shared_kpi_selection DROP CONSTRAINT IF EXISTS toolkit_shared_kpi_selection_deleted_by_fkey;
ALTER TABLE toolkit_shared_kpi_selection DROP CONSTRAINT IF EXISTS toolkit_shared_kpi_selection_updated_by_fkey;
ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_created_by_fkey;
ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_deleted_by_fkey;
ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_owner_user_id_fkey;
ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_updated_by_fkey;
ALTER TABLE exercise_toolkit_snapshot DROP CONSTRAINT IF EXISTS exercise_toolkit_snapshot_created_by_fkey;
ALTER TABLE exercise_shared_kpi_line DROP CONSTRAINT IF EXISTS exercise_shared_kpi_line_created_by_fkey;
ALTER TABLE file_artifact DROP CONSTRAINT IF EXISTS file_artifact_created_by_fkey;
ALTER TABLE exercise_team_setup DROP CONSTRAINT IF EXISTS exercise_team_setup_created_by_fkey;
ALTER TABLE exercise_team_setup DROP CONSTRAINT IF EXISTS exercise_team_setup_updated_by_fkey;
ALTER TABLE exercise_shift DROP CONSTRAINT IF EXISTS exercise_shift_created_by_fkey;
ALTER TABLE exercise_shift DROP CONSTRAINT IF EXISTS exercise_shift_deleted_by_fkey;
ALTER TABLE exercise_shift DROP CONSTRAINT IF EXISTS exercise_shift_updated_by_fkey;
ALTER TABLE exercise_production_support_item DROP CONSTRAINT IF EXISTS production_support_item_created_by_fkey;
ALTER TABLE exercise_production_support_item DROP CONSTRAINT IF EXISTS production_support_item_deleted_by_fkey;
ALTER TABLE exercise_production_support_item DROP CONSTRAINT IF EXISTS production_support_item_updated_by_fkey;
ALTER TABLE exercise_calendar DROP CONSTRAINT IF EXISTS exercise_calendar_created_by_fkey;
ALTER TABLE exercise_calendar DROP CONSTRAINT IF EXISTS exercise_calendar_updated_by_fkey;
ALTER TABLE exercise_holiday DROP CONSTRAINT IF EXISTS exercise_holiday_created_by_fkey;
ALTER TABLE exercise_holiday DROP CONSTRAINT IF EXISTS exercise_holiday_deleted_by_fkey;
ALTER TABLE exercise_holiday DROP CONSTRAINT IF EXISTS exercise_holiday_updated_by_fkey;
ALTER TABLE data_import_batch DROP CONSTRAINT IF EXISTS data_import_batch_created_by_fkey;
ALTER TABLE exercise_volume_monthly_input DROP CONSTRAINT IF EXISTS volume_monthly_input_created_by_fkey;
ALTER TABLE exercise_volume_monthly_input DROP CONSTRAINT IF EXISTS volume_monthly_input_updated_by_fkey;
ALTER TABLE exercise_volume_daily_input DROP CONSTRAINT IF EXISTS volume_daily_input_created_by_fkey;
ALTER TABLE exercise_volume_daily_input DROP CONSTRAINT IF EXISTS volume_daily_input_updated_by_fkey;
ALTER TABLE exercise_volume_slot_input DROP CONSTRAINT IF EXISTS volume_slot_input_created_by_fkey;
ALTER TABLE exercise_volume_slot_input DROP CONSTRAINT IF EXISTS volume_slot_input_updated_by_fkey;
ALTER TABLE exercise_tms_session DROP CONSTRAINT IF EXISTS exercise_tms_session_selected_by_fkey;
ALTER TABLE cycle_time_baseline DROP CONSTRAINT IF EXISTS cycle_time_baseline_calculated_by_fkey;
ALTER TABLE cycle_time_baseline_file DROP CONSTRAINT IF EXISTS cycle_time_baseline_file_created_by_fkey;
ALTER TABLE scenario DROP CONSTRAINT IF EXISTS scenario_created_by_fkey;
ALTER TABLE scenario DROP CONSTRAINT IF EXISTS scenario_deleted_by_fkey;
ALTER TABLE scenario DROP CONSTRAINT IF EXISTS scenario_official_by_fkey;
ALTER TABLE scenario DROP CONSTRAINT IF EXISTS scenario_updated_by_fkey;
ALTER TABLE scenario_assumption DROP CONSTRAINT IF EXISTS scenario_assumption_created_by_fkey;
ALTER TABLE scenario_assumption DROP CONSTRAINT IF EXISTS scenario_assumption_updated_by_fkey;
ALTER TABLE forecast_run DROP CONSTRAINT IF EXISTS forecast_run_created_by_fkey;
ALTER TABLE simulation_run DROP CONSTRAINT IF EXISTS simulation_run_created_by_fkey;
ALTER TABLE validation_result DROP CONSTRAINT IF EXISTS validation_result_evaluated_by_fkey;
ALTER TABLE official_package DROP CONSTRAINT IF EXISTS official_package_created_by_fkey;
ALTER TABLE submission DROP CONSTRAINT IF EXISTS submission_submitted_by_fkey;
ALTER TABLE workflow_step_assignment DROP CONSTRAINT IF EXISTS workflow_step_assignment_assignee_user_id_fkey;
ALTER TABLE workflow_action DROP CONSTRAINT IF EXISTS workflow_action_actor_user_id_fkey;
ALTER TABLE center_holiday_template DROP CONSTRAINT IF EXISTS center_holiday_template_created_by_fkey;
ALTER TABLE center_holiday_template DROP CONSTRAINT IF EXISTS center_holiday_template_deleted_by_fkey;
ALTER TABLE center_holiday_template DROP CONSTRAINT IF EXISTS center_holiday_template_published_by_fkey;
ALTER TABLE center_holiday_template DROP CONSTRAINT IF EXISTS center_holiday_template_updated_by_fkey;
ALTER TABLE center_holiday_template_line DROP CONSTRAINT IF EXISTS center_holiday_template_line_created_by_fkey;
ALTER TABLE center_holiday_template_line DROP CONSTRAINT IF EXISTS center_holiday_template_line_deleted_by_fkey;
ALTER TABLE center_holiday_template_line DROP CONSTRAINT IF EXISTS center_holiday_template_line_updated_by_fkey;
ALTER TABLE center_holiday_template_snapshot DROP CONSTRAINT IF EXISTS center_holiday_template_snapshot_published_by_fkey;
ALTER TABLE scenario_shift DROP CONSTRAINT IF EXISTS scenario_shift_created_by_fkey;
ALTER TABLE scenario_shift DROP CONSTRAINT IF EXISTS scenario_shift_updated_by_fkey;

ALTER TABLE tms_session ADD COLUMN agent_ccgid VARCHAR(64);
ALTER TABLE tms_session ADD COLUMN agent_name_snapshot VARCHAR(160) NOT NULL DEFAULT '';
UPDATE tms_session t
SET agent_ccgid = COALESCE(u.ccgid, 'UNKNOWN'),
    agent_name_snapshot = COALESCE(u.display_name, '')
FROM app_user u
WHERE u.id = t.agent_user_id;
UPDATE tms_session SET agent_ccgid = 'UNKNOWN' WHERE agent_ccgid IS NULL;
ALTER TABLE tms_session ALTER COLUMN agent_ccgid SET NOT NULL;
ALTER TABLE tms_session DROP COLUMN agent_user_id;

ALTER TABLE toolkit ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE toolkit DROP COLUMN created_by;
ALTER TABLE toolkit RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE toolkit ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE toolkit DROP COLUMN deleted_by;
ALTER TABLE toolkit RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE toolkit ADD COLUMN owner_ccgid VARCHAR(64);
UPDATE toolkit t SET owner_ccgid = u.ccgid FROM app_user u WHERE u.id = t.owner_user_id;
ALTER TABLE toolkit DROP COLUMN owner_user_id;

ALTER TABLE toolkit ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE toolkit DROP COLUMN updated_by;
ALTER TABLE toolkit RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE toolkit_subtask ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit_subtask t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE toolkit_subtask DROP COLUMN created_by;
ALTER TABLE toolkit_subtask RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE toolkit_subtask ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit_subtask t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE toolkit_subtask DROP COLUMN deleted_by;
ALTER TABLE toolkit_subtask RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE toolkit_subtask ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit_subtask t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE toolkit_subtask DROP COLUMN updated_by;
ALTER TABLE toolkit_subtask RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE toolkit_shared_kpi_selection ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit_shared_kpi_selection t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE toolkit_shared_kpi_selection DROP COLUMN created_by;
ALTER TABLE toolkit_shared_kpi_selection RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE toolkit_shared_kpi_selection ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit_shared_kpi_selection t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE toolkit_shared_kpi_selection DROP COLUMN deleted_by;
ALTER TABLE toolkit_shared_kpi_selection RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE toolkit_shared_kpi_selection ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE toolkit_shared_kpi_selection t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE toolkit_shared_kpi_selection DROP COLUMN updated_by;
ALTER TABLE toolkit_shared_kpi_selection RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE rst_exercise ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE rst_exercise t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE rst_exercise DROP COLUMN created_by;
ALTER TABLE rst_exercise RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE rst_exercise ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE rst_exercise t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE rst_exercise DROP COLUMN deleted_by;
ALTER TABLE rst_exercise RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE rst_exercise ADD COLUMN owner_ccgid VARCHAR(64);
UPDATE rst_exercise t SET owner_ccgid = u.ccgid FROM app_user u WHERE u.id = t.owner_user_id;
UPDATE rst_exercise SET owner_ccgid = 'UNKNOWN' WHERE owner_ccgid IS NULL;
ALTER TABLE rst_exercise ALTER COLUMN owner_ccgid SET NOT NULL;
ALTER TABLE rst_exercise DROP COLUMN owner_user_id;

ALTER TABLE rst_exercise ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE rst_exercise t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE rst_exercise DROP COLUMN updated_by;
ALTER TABLE rst_exercise RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_toolkit_snapshot ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_toolkit_snapshot t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_toolkit_snapshot DROP COLUMN created_by;
ALTER TABLE exercise_toolkit_snapshot RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_shared_kpi_line ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_shared_kpi_line t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_shared_kpi_line DROP COLUMN created_by;
ALTER TABLE exercise_shared_kpi_line RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE file_artifact ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE file_artifact t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE file_artifact DROP COLUMN created_by;
ALTER TABLE file_artifact RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_team_setup ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_team_setup t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_team_setup DROP COLUMN created_by;
ALTER TABLE exercise_team_setup RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_team_setup ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_team_setup t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_team_setup DROP COLUMN updated_by;
ALTER TABLE exercise_team_setup RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_shift ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_shift t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_shift DROP COLUMN created_by;
ALTER TABLE exercise_shift RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_shift ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_shift t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE exercise_shift DROP COLUMN deleted_by;
ALTER TABLE exercise_shift RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE exercise_shift ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_shift t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_shift DROP COLUMN updated_by;
ALTER TABLE exercise_shift RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_production_support_item ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_production_support_item t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_production_support_item DROP COLUMN created_by;
ALTER TABLE exercise_production_support_item RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_production_support_item ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_production_support_item t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE exercise_production_support_item DROP COLUMN deleted_by;
ALTER TABLE exercise_production_support_item RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE exercise_production_support_item ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_production_support_item t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_production_support_item DROP COLUMN updated_by;
ALTER TABLE exercise_production_support_item RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_calendar ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_calendar t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_calendar DROP COLUMN created_by;
ALTER TABLE exercise_calendar RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_calendar ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_calendar t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_calendar DROP COLUMN updated_by;
ALTER TABLE exercise_calendar RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_holiday ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_holiday t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_holiday DROP COLUMN created_by;
ALTER TABLE exercise_holiday RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_holiday ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_holiday t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE exercise_holiday DROP COLUMN deleted_by;
ALTER TABLE exercise_holiday RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE exercise_holiday ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_holiday t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_holiday DROP COLUMN updated_by;
ALTER TABLE exercise_holiday RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE data_import_batch ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE data_import_batch t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE data_import_batch DROP COLUMN created_by;
ALTER TABLE data_import_batch RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_volume_monthly_input ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_volume_monthly_input t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_volume_monthly_input DROP COLUMN created_by;
ALTER TABLE exercise_volume_monthly_input RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_volume_monthly_input ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_volume_monthly_input t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_volume_monthly_input DROP COLUMN updated_by;
ALTER TABLE exercise_volume_monthly_input RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_volume_daily_input ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_volume_daily_input t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_volume_daily_input DROP COLUMN created_by;
ALTER TABLE exercise_volume_daily_input RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_volume_daily_input ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_volume_daily_input t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_volume_daily_input DROP COLUMN updated_by;
ALTER TABLE exercise_volume_daily_input RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_volume_slot_input ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_volume_slot_input t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE exercise_volume_slot_input DROP COLUMN created_by;
ALTER TABLE exercise_volume_slot_input RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE exercise_volume_slot_input ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_volume_slot_input t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE exercise_volume_slot_input DROP COLUMN updated_by;
ALTER TABLE exercise_volume_slot_input RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE exercise_tms_session ADD COLUMN selected_by_ccgid_tmp VARCHAR(64);
UPDATE exercise_tms_session t SET selected_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.selected_by;
ALTER TABLE exercise_tms_session DROP COLUMN selected_by;
ALTER TABLE exercise_tms_session RENAME COLUMN selected_by_ccgid_tmp TO selected_by;

ALTER TABLE cycle_time_baseline ADD COLUMN calculated_by_ccgid_tmp VARCHAR(64);
UPDATE cycle_time_baseline t SET calculated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.calculated_by;
ALTER TABLE cycle_time_baseline DROP COLUMN calculated_by;
ALTER TABLE cycle_time_baseline RENAME COLUMN calculated_by_ccgid_tmp TO calculated_by;

ALTER TABLE cycle_time_baseline_file ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE cycle_time_baseline_file t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE cycle_time_baseline_file DROP COLUMN created_by;
ALTER TABLE cycle_time_baseline_file RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE scenario ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE scenario t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE scenario DROP COLUMN created_by;
ALTER TABLE scenario RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE scenario ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE scenario t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE scenario DROP COLUMN deleted_by;
ALTER TABLE scenario RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE scenario ADD COLUMN official_by_ccgid_tmp VARCHAR(64);
UPDATE scenario t SET official_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.official_by;
ALTER TABLE scenario DROP COLUMN official_by;
ALTER TABLE scenario RENAME COLUMN official_by_ccgid_tmp TO official_by;

ALTER TABLE scenario ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE scenario t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE scenario DROP COLUMN updated_by;
ALTER TABLE scenario RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE scenario_assumption ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE scenario_assumption t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE scenario_assumption DROP COLUMN created_by;
ALTER TABLE scenario_assumption RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE scenario_assumption ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE scenario_assumption t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE scenario_assumption DROP COLUMN updated_by;
ALTER TABLE scenario_assumption RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE forecast_run ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE forecast_run t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE forecast_run DROP COLUMN created_by;
ALTER TABLE forecast_run RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE simulation_run ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE simulation_run t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE simulation_run DROP COLUMN created_by;
ALTER TABLE simulation_run RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE validation_result ADD COLUMN evaluated_by_ccgid_tmp VARCHAR(64);
UPDATE validation_result t SET evaluated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.evaluated_by;
ALTER TABLE validation_result DROP COLUMN evaluated_by;
ALTER TABLE validation_result RENAME COLUMN evaluated_by_ccgid_tmp TO evaluated_by;

ALTER TABLE official_package ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE official_package t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE official_package DROP COLUMN created_by;
ALTER TABLE official_package RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE submission ADD COLUMN submitted_by_ccgid VARCHAR(64);
UPDATE submission t SET submitted_by_ccgid = u.ccgid FROM app_user u WHERE u.id = t.submitted_by;
UPDATE submission SET submitted_by_ccgid = 'UNKNOWN' WHERE submitted_by_ccgid IS NULL;
ALTER TABLE submission ALTER COLUMN submitted_by_ccgid SET NOT NULL;
ALTER TABLE submission DROP COLUMN submitted_by;

ALTER TABLE workflow_step_assignment ADD COLUMN assignee_ccgid VARCHAR(64);
UPDATE workflow_step_assignment t SET assignee_ccgid = u.ccgid FROM app_user u WHERE u.id = t.assignee_user_id;
ALTER TABLE workflow_step_assignment DROP COLUMN assignee_user_id;

ALTER TABLE workflow_action ADD COLUMN actor_ccgid VARCHAR(64);
UPDATE workflow_action t SET actor_ccgid = u.ccgid FROM app_user u WHERE u.id = t.actor_user_id;
UPDATE workflow_action SET actor_ccgid = 'UNKNOWN' WHERE actor_ccgid IS NULL;
ALTER TABLE workflow_action ALTER COLUMN actor_ccgid SET NOT NULL;
ALTER TABLE workflow_action DROP COLUMN actor_user_id;

ALTER TABLE center_holiday_template ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE center_holiday_template DROP COLUMN created_by;
ALTER TABLE center_holiday_template RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE center_holiday_template ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE center_holiday_template DROP COLUMN deleted_by;
ALTER TABLE center_holiday_template RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE center_holiday_template ADD COLUMN published_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template t SET published_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.published_by;
ALTER TABLE center_holiday_template DROP COLUMN published_by;
ALTER TABLE center_holiday_template RENAME COLUMN published_by_ccgid_tmp TO published_by;

ALTER TABLE center_holiday_template ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE center_holiday_template DROP COLUMN updated_by;
ALTER TABLE center_holiday_template RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE center_holiday_template_line ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template_line t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE center_holiday_template_line DROP COLUMN created_by;
ALTER TABLE center_holiday_template_line RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE center_holiday_template_line ADD COLUMN deleted_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template_line t SET deleted_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.deleted_by;
ALTER TABLE center_holiday_template_line DROP COLUMN deleted_by;
ALTER TABLE center_holiday_template_line RENAME COLUMN deleted_by_ccgid_tmp TO deleted_by;

ALTER TABLE center_holiday_template_line ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template_line t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE center_holiday_template_line DROP COLUMN updated_by;
ALTER TABLE center_holiday_template_line RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

ALTER TABLE center_holiday_template_snapshot ADD COLUMN published_by_ccgid_tmp VARCHAR(64);
UPDATE center_holiday_template_snapshot t SET published_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.published_by;
ALTER TABLE center_holiday_template_snapshot DROP COLUMN published_by;
ALTER TABLE center_holiday_template_snapshot RENAME COLUMN published_by_ccgid_tmp TO published_by;

ALTER TABLE scenario_shift ADD COLUMN created_by_ccgid_tmp VARCHAR(64);
UPDATE scenario_shift t SET created_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.created_by;
ALTER TABLE scenario_shift DROP COLUMN created_by;
ALTER TABLE scenario_shift RENAME COLUMN created_by_ccgid_tmp TO created_by;

ALTER TABLE scenario_shift ADD COLUMN updated_by_ccgid_tmp VARCHAR(64);
UPDATE scenario_shift t SET updated_by_ccgid_tmp = u.ccgid FROM app_user u WHERE u.id = t.updated_by;
ALTER TABLE scenario_shift DROP COLUMN updated_by;
ALTER TABLE scenario_shift RENAME COLUMN updated_by_ccgid_tmp TO updated_by;

DROP TABLE IF EXISTS app_user;
