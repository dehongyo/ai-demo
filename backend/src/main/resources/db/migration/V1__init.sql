create table app_user (
    id uuid primary key,
    display_name varchar(100) not null,
    created_at timestamp not null default now()
);

create table chat_session (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    title varchar(200) not null,
    version bigint not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);
create index idx_chat_session_user_updated
    on chat_session(user_id, updated_at desc);

create table chat_message (
    id uuid primary key,
    session_id uuid not null references chat_session(id),
    role varchar(30) not null
        check (role in ('USER', 'ASSISTANT', 'ASSISTANT_TOOL_CALL', 'TOOL')),
    content text not null,
    tool_call_id varchar(200),
    tool_name varchar(100),
    tool_calls_json text,
    sequence_no bigint not null,
    created_at timestamp not null default now(),
    unique(session_id, sequence_no)
);
create index idx_chat_message_session_sequence
    on chat_message(session_id, sequence_no);

create table session_summary (
    session_id uuid primary key references chat_session(id),
    content text not null,
    covered_until_message_id uuid not null references chat_message(id),
    updated_at timestamp not null default now()
);

create table todo_item (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    session_id uuid not null references chat_session(id),
    content varchar(500) not null,
    status varchar(20) not null
        check (status in ('OPEN', 'COMPLETED')),
    created_at timestamp not null default now(),
    completed_at timestamp
);
create index idx_todo_scope_status
    on todo_item(user_id, session_id, status, created_at);

create table agent_run (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    session_id uuid not null references chat_session(id),
    trigger_message_id uuid not null references chat_message(id),
    status varchar(30) not null,
    final_answer text,
    total_prompt_tokens integer not null default 0,
    total_completion_tokens integer not null default 0,
    started_at timestamp not null default now(),
    finished_at timestamp,
    error_code varchar(100),
    error_message varchar(1000)
);
create index idx_agent_run_session_started
    on agent_run(session_id, started_at desc);

create table agent_step (
    id uuid primary key,
    run_id uuid not null references agent_run(id),
    step_number integer not null,
    decision_type varchar(30) not null,
    decision_summary varchar(1000),
    model_request_id varchar(200),
    finish_reason varchar(100),
    prompt_tokens integer not null default 0,
    completion_tokens integer not null default 0,
    duration_ms bigint not null,
    created_at timestamp not null default now(),
    unique(run_id, step_number)
);

create table tool_execution (
    id uuid primary key,
    step_id uuid not null references agent_step(id),
    tool_call_id varchar(200) not null,
    tool_name varchar(100) not null,
    arguments_json text not null,
    success boolean not null,
    result_json text,
    error_code varchar(100),
    duration_ms bigint not null,
    created_at timestamp not null default now()
);
