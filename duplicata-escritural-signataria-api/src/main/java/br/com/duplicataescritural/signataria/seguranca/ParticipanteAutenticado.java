package br.com.duplicataescritural.signataria.seguranca;

public sealed interface ParticipanteAutenticado permits SacadorAutenticado, FinanciadorAutenticado { }
