"""Small typed LangGraph wrapper: retrieve evidence, then answer or abstain."""

from typing import TypedDict
from langgraph.graph import END, START, StateGraph


class AskState(TypedDict, total=False):
    question: str
    evidence_count: int
    route: str


def build_ask_graph():
    def assess(state: AskState) -> AskState:
        return {"route": "answer" if state.get("evidence_count", 0) else "abstain"}

    graph = StateGraph(AskState)
    graph.add_node("assess_evidence", assess)
    graph.add_edge(START, "assess_evidence")
    graph.add_conditional_edges("assess_evidence", lambda state: state["route"], {"answer": END, "abstain": END})
    return graph.compile()
