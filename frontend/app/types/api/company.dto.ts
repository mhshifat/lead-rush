export interface CompanyApiDto {
  id: string
  name: string
  domain: string | null
  industry: string | null
  companySize: string | null
  annualRevenue: string | null
  description: string | null
  website: string | null
  logoUrl: string | null
  phone: string | null
  address: string | null
  city: string | null
  state: string | null
  country: string | null
  zipCode: string | null
  contactCount: number
  createdAt: string
  updatedAt: string
}

export interface CreateCompanyDto {
  name: string
  domain?: string
  industry?: string
  companySize?: string
  annualRevenue?: string
  description?: string
  website?: string
  logoUrl?: string
  phone?: string
  address?: string
  city?: string
  state?: string
  country?: string
  zipCode?: string
}
